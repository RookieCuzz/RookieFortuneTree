package com.cuzz.rookiefortunetree.mapper;

import com.cuzz.rookiefortunetree.repository.model.FortuneTreeAttemptRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FortuneTreeAttemptMapper {
    int createTableIfNotExists();

    FortuneTreeAttemptRecord selectById(@Param("uuid") String uuid, @Param("cycleId") String cycleId);

    int insert(FortuneTreeAttemptRecord record);

    int update(FortuneTreeAttemptRecord record);
}

