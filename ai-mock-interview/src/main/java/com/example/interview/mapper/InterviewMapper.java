package com.example.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.domain.Interview;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterviewMapper extends BaseMapper<Interview> {
  @Select("SELECT * FROM mi_interview WHERE id = #{id} FOR UPDATE")
  Interview lock(@Param("id") Long id);
}
