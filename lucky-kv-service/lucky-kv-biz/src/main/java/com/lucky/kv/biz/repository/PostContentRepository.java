package com.lucky.kv.biz.repository;

import com.lucky.kv.biz.entity.PostContent;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

public interface PostContentRepository extends CassandraRepository<PostContent, UUID> {
}
