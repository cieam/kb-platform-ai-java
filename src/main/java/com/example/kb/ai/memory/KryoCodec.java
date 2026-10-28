package com.example.kb.ai.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kryo 编解码。
 *
 * <p>选 Kryo 而不是 JSON 的原因：对话记忆每轮都要读写，体积和速度都敏感。
 * 同样一段历史，JSON 大约 1.2KB，Kryo 后不到 400B，Redis 带宽和 MySQL 存储都省一截。
 *
 * <p>线程安全：Kryo 实例本身不是线程安全的，这里用 ThreadLocal 每线程一个，
 * 避免为了线程安全去加锁（加锁会把并发读记忆变成串行）。
 */
@Component
public class KryoCodec {

    private final ThreadLocal<Kryo> holder = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 不强制注册：允许序列化未显式注册的类，代价是体积略大
        kryo.setRegistrationRequired(false);
        kryo.register(ArrayList.class);
        kryo.register(StoredTurn.class);
        return kryo;
    });

    public byte[] encode(List<StoredTurn> turns) {
        try (Output output = new Output(512, -1)) {
            holder.get().writeObject(output, new ArrayList<>(turns));
            return output.toBytes();
        }
    }

    @SuppressWarnings("unchecked")
    public List<StoredTurn> decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return new ArrayList<>();
        }
        try (Input input = new Input(payload)) {
            Object decoded = holder.get().readObject(input, ArrayList.class);
            return decoded == null ? new ArrayList<>() : (List<StoredTurn>) decoded;
        }
    }
}
