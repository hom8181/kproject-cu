package com.ohdocha.cu.kprojectcu.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Alias("DochaMainDto")
public class DochaMainDto {
	
	private int miIdx;
	private String miImgIdx;
	private LocalDateTime miStartDt;
	private LocalDateTime miEndDt;
	private String miTitle;
	private String regId;
	private LocalDateTime regDt;
	private String modId;
	private LocalDateTime modDt;

  
}