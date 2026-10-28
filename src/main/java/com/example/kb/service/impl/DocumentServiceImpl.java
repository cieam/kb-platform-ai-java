package com.example.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kb.ai.AiClient;
import com.example.kb.common.exception.BizException;
import com.example.kb.common.result.PageResult;
import com.example.kb.common.result.ResultCode;
import com.example.kb.common.util.LangChainTextSplitter;
import com.example.kb.config.AsyncConfig;
import com.example.kb.config.KbProperties;
import com.example.kb.domain.entity.KbDocument;
import com.example.kb.domain.entity.KnowledgeBase;
import com.example.kb.domain.vo.DocumentVO;
import com.example.kb.mapper.KbDocumentMapper;
import com.example.kb.service.DocumentService;
import com.example.kb.service.KnowledgeBaseService;
import com.example.kb.vector.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档流水线：落盘 → 解析 → 切分 → 向量化 → 写 pgvector。
 *
 * <p>全流程跑在 aiExecutor 里，避免大文件把 Tomcat 的 Web 线程占满。
 * 注意这里是「线程池显式提交」而不是 {@code @Async}：同类内部调用不走 Spring 代理，
 * 加 {@code @Async} 会静默失效，这是最常见的翻车点。
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    /** 抽出来的文本短于这个长度就认为没解析成功 */
    private static final int MIN_TEXT_LENGTH = 10;
    /** 大于这个体积、又没抽到文本，基本可以判定是扫描件 */
    private static final long SCAN_HINT_FILE_SIZE = 100 * 1024L;

    private final KbDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorRepository vectorRepository;
    private final AiClient aiClient;
    /** LangChain4j 递归切分器，替代原来手写的两段式切分 */
    private final LangChainTextSplitter textSplitter;
    private final KbProperties kbProperties;
    private final ThreadPoolTaskExecutor aiExecutor;
    private final StringRedisTemplate redisTemplate;

    public DocumentServiceImpl(KbDocumentMapper documentMapper,
                               KnowledgeBaseService knowledgeBaseService,
                               VectorRepository vectorRepository,
                               AiClient aiClient,
                               LangChainTextSplitter textSplitter,
                               KbProperties kbProperties,
                               @Qualifier(AsyncConfig.AI_EXECUTOR) ThreadPoolTaskExecutor aiExecutor,
                               StringRedisTemplate redisTemplate) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.vectorRepository = vectorRepository;
        this.aiClient = aiClient;
        this.textSplitter = textSplitter;
        this.kbProperties = kbProperties;
        this.aiExecutor = aiExecutor;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public DocumentVO upload(Long kbId, MultipartFile file) {
        knowledgeBaseService.getOwned(kbId);
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        Path dir = Paths.get(kbProperties.getStorage().getLocalPath(), String.valueOf(kbId));
        Path target = dir.resolve(UUID.randomUUID().toString().replace("-", "")
                + (ext.isEmpty() ? "" : "." + ext));
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toAbsolutePath().toFile());
        } catch (IOException e) {
            throw new BizException(ResultCode.BIZ_ERROR, "文件保存失败：" + e.getMessage());
        }

        KbDocument document = new KbDocument();
        document.setKbId(kbId);
        document.setFileName(originalName);
        document.setFileType(ext);
        document.setFileSize(file.getSize());
        document.setFilePath(target.toAbsolutePath().toString());
        document.setStatus(KbDocument.STATUS_PENDING);
        documentMapper.insert(document);
        knowledgeBaseService.increaseDocCount(kbId, 1);

        submit(document.getId());
        return DocumentVO.from(documentMapper.selectById(document.getId()));
    }

    @Override
    public void reparse(Long id) {
        KbDocument document = requireDocument(id);
        document.setStatus(KbDocument.STATUS_PENDING);
        document.setErrorMsg(null);
        documentMapper.updateById(document);
        submit(document.getId());
    }

    private void submit(Long docId) {
        aiExecutor.execute(() -> process(docId));
    }

    @Override
    public void process(Long docId) {
        KbDocument document = documentMapper.selectById(docId);
        if (document == null) {
            return;
        }
        long start = System.currentTimeMillis();
        document.setStatus(KbDocument.STATUS_PARSING);
        document.setErrorMsg(null);
        documentMapper.updateById(document);

        try {
            // 异步线程里没有 UserContext，只能用不做鉴权的 getById
            KnowledgeBase kb = knowledgeBaseService.getById(document.getKbId());

            String text = parseToText(new File(document.getFilePath()));
            assertTextExtracted(text, document);

            List<String> chunks = textSplitter.split(text, kb.getChunkSize(), kb.getChunkOverlap());
            if (chunks.isEmpty()) {
                throw new BizException(ResultCode.BIZ_ERROR, "切分后没有内容，请检查文档格式");
            }
            // 给每段加上文件名前缀，能明显改善「问条款类问题召回不到」的情况
            String prefix = "【" + document.getFileName() + "】";
            List<String> enriched = chunks.stream().map(c -> prefix + c).collect(Collectors.toList());

            // 分批调用 + 退避重试都在 AiClient 内部完成
            List<float[]> vectors = aiClient.embedBatch(enriched);

            // 重复解析时先清掉旧向量，保证幂等
            vectorRepository.deleteByDocId(docId);
            vectorRepository.saveAll(document.getKbId(), docId, enriched, vectors);

            document.setStatus(KbDocument.STATUS_SUCCESS);
            document.setChunkCount(enriched.size());
            document.setErrorMsg(null);
            document.setParseCostMs(System.currentTimeMillis() - start);
            documentMapper.updateById(document);
            // 文档内容变了，之前基于旧内容生成的问答缓存必须失效，
            // 否则用户会一直拿到旧答案（缓存 TTL 30 分钟也够误导人）
            clearAnswerCache(document.getKbId());
            log.info("文档解析完成 docId={} chunkCount={} costMs={}",
                    docId, enriched.size(), document.getParseCostMs());
        } catch (Exception e) {
            log.error("文档解析失败 docId=" + docId, e);
            document.setStatus(KbDocument.STATUS_FAILED);
            document.setErrorMsg(trim(e.getMessage(), 900));
            document.setParseCostMs(System.currentTimeMillis() - start);
            documentMapper.updateById(document);
        }
    }

    @Override
    public DocumentVO get(Long id) {
        return DocumentVO.from(requireDocument(id));
    }

    @Override
    public PageResult<DocumentVO> page(Long kbId, long page, long size) {
        knowledgeBaseService.getOwned(kbId);
        Page<KbDocument> query = new Page<>(page, size);
        Page<KbDocument> result = documentMapper.selectPage(query,
                new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getId));
        List<DocumentVO> list = result.getRecords().stream()
                .map(DocumentVO::from).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public void delete(Long id) {
        KbDocument document = requireDocument(id);
        documentMapper.deleteById(id);
        vectorRepository.deleteByDocId(id);
        knowledgeBaseService.increaseDocCount(document.getKbId(), -1);
        clearAnswerCache(document.getKbId());
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            log.warn("删除本地文件失败 path={}", document.getFilePath(), e);
        }
    }

    // ================= 内部方法 =================

    private KbDocument requireDocument(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "文档 ID 不能为空");
        }
        KbDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BizException(ResultCode.NOT_FOUND, "文档不存在");
        }
        knowledgeBaseService.getOwned(document.getKbId());
        return document;
    }

    /**
     * 扫描件识别：Tika 对图片型 PDF 抽不出文本，这时直接给出可操作的结论，
     * 而不是让用户对着一个空知识库猜原因。
     */
    private void assertTextExtracted(String text, KbDocument document) {
        if (text != null && text.strip().length() >= MIN_TEXT_LENGTH) {
            return;
        }
        boolean likelyScanned = "pdf".equalsIgnoreCase(document.getFileType())
                && document.getFileSize() != null
                && document.getFileSize() > SCAN_HINT_FILE_SIZE;
        String reason = likelyScanned
                ? "文件体积较大但几乎没抽到文本，基本可以判定是扫描件（图片型 PDF）。"
                + "需要先用 OCR 转成文本再入库，当前版本不支持。"
                : "没有解析出有效文本，请确认文件不是空文件或纯图片。";
        throw new BizException(ResultCode.BIZ_ERROR, reason);
    }

    private String parseToText(File file) throws Exception {
        Tika tika = new Tika();
        // 默认上限 10 万字符，长文档会被悄悄截断，这里放开
        tika.setMaxStringLength(-1);
        return tika.parseToString(file);
    }

    private static String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase();
    }

    /**
     * 清掉某个知识库下的问答缓存。
     *
     * <p>这里用 KEYS 是因为单库缓存量很小（一个问题一个 key）；
     * 生产环境换成 SCAN 游标遍历，避免大 key 空间下阻塞 Redis。
     */
    private void clearAnswerCache(Long kbId) {
        try {
            Set<String> keys = redisTemplate.keys("kb:answer:" + kbId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("知识库 {} 的问答缓存已清理 {} 条", kbId, keys.size());
            }
        } catch (Exception e) {
            // 缓存清理失败不能影响文档处理结果
            log.warn("清理问答缓存失败 kbId={}", kbId, e);
        }
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
