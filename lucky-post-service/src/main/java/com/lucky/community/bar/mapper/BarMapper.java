package com.lucky.community.bar.mapper;

import com.lucky.community.bar.entity.Bar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BarMapper {

    int insert(Bar bar);

    Bar selectById(@Param("id") Long id);

    int updateById(Bar bar);

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    long countPage(@Param("status") Integer status, @Param("keyword") String keyword);

    List<Bar> selectPage(@Param("status") Integer status,
                         @Param("keyword") String keyword,
                         @Param("limit") int limit,
                         @Param("offset") int offset);

    List<Bar> selectByIds(@Param("ids") List<Long> ids);
}
