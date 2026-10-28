package com.example.kb.ai;

import com.example.kb.config.KbProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型路由规则的单测。
 *
 * <p>路由直接决定 API 成本，所以规则要能被测试固定住：
 * 短问题 + 无复杂意图词 → 本地小模型；其余一律走云端。
 */
class ChatEngineRoutingTest {

    private ChatEngine engine(boolean localEnabled, int maxLength) {
        KbProperties properties = new KbProperties();
        properties.getAi().getLocal().setEnabled(localEnabled);
        properties.getAi().getLocal().setMaxQuestionLength(maxLength);
        // 只测路由判断，模型注册表 / 工具 / 模拟实现都不参与，传 null 即可
        return new ChatEngine(null, null, properties, null);
    }

    @Test
    void 短问题走本地模型() {
        assertTrue(engine(true, 40).preferLocal("年假有几天？"));
    }

    @Test
    void 含复杂意图词走云端() {
        assertFalse(engine(true, 40).preferLocal("对比一下两版报销制度的区别"));
    }

    @Test
    void 超长问题走云端() {
        // 阈值 5，问题 15 个字，必须走云端
        assertFalse(engine(true, 5).preferLocal("请问公司的年假和调休分别是几天"));
    }

    @Test
    void 开关关闭时永远走云端() {
        assertFalse(engine(false, 200).preferLocal("年假"));
    }

    @Test
    void 空问题不崩溃且走云端() {
        assertFalse(engine(true, 40).preferLocal(null));
    }
}
