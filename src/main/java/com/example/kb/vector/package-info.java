/**
 * 向量存储。
 *
 * <p>用 JdbcTemplate 直接读写 PostgreSQL + pgvector，因为 vector 类型没有现成 ORM 映射。所有检索 SQL 都强制带 kb_id 条件，多租户隔离在这一层兜住。
 */
package com.example.kb.vector;
