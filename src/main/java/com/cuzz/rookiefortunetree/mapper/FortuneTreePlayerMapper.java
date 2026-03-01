package com.cuzz.rookiefortunetree.mapper;

import com.cuzz.rookiefortunetree.repository.model.FortuneTreePlayerRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FortuneTreePlayerMapper {
    int createTableIfNotExists();

    FortuneTreePlayerRecord selectByUuid(@Param("uuid") String uuid);

    int insert(FortuneTreePlayerRecord record);

    int update(FortuneTreePlayerRecord record);
}

