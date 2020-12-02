package com.ohdocha.cu.kprojectcu.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import com.ohdocha.cu.kprojectcu.domain.DochaScheduledDto;
import com.ohdocha.cu.kprojectcu.util.DochaMap;

@Mapper
@Component
public interface DochaScheduledDao {

	public List<DochaScheduledDto> selectScheduledList(DochaMap param);
}