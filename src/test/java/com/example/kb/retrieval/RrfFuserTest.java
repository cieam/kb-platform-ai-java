package com.example.kb.retrieval;

import com.example.kb.vector.ChunkVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFuserTest {

    private static ChunkVector chunk(long id) {
        ChunkVector vector = new ChunkVector();
        vector.setId(id);
        vector.setContent("chunk-" + id);
        return vector;
    }

    @Test
    void itemFoundByBothRetrieversRanksFirst() {
        List<ChunkVector> fused = RrfFuser.fuse(60, 10,
                List.of(chunk(1), chunk(2), chunk(3)),
                List.of(chunk(3), chunk(4)));
        assertEquals(3L, fused.get(0).getId(), "两路都召回到的条目应当排第一");
    }

    @Test
    void duplicatesAreMerged() {
        List<ChunkVector> fused = RrfFuser.fuse(60, 10,
                List.of(chunk(1), chunk(2)), List.of(chunk(2), chunk(1)));
        assertEquals(2, fused.size());
    }

    @Test
    void topNIsApplied() {
        List<ChunkVector> fused = RrfFuser.fuse(60, 2,
                List.of(chunk(1), chunk(2), chunk(3)), List.of(chunk(4), chunk(5)));
        assertEquals(2, fused.size());
    }

    @Test
    void nullListIsTolerated() {
        assertTrue(RrfFuser.fuse(60, 5, null, List.of(chunk(1))).size() == 1);
    }
}
