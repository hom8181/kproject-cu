package com.ohdocha.cu.kprojectcu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.ohdocha.cu.kprojectcu.domain.*;
import com.ohdocha.cu.kprojectcu.util.DochaMap;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public interface DochaPaymentService {

	public int insertReserveMaster(DochaPaymentDto paymentReserveMasterDto);
	
	public int insertReserve(DochaPaymentReserveDto paramMap);
	
	public int insertPaymentDetail(DochaPaymentDetailDto paramMap);
	
	public int insertPaymentLog(DochaPaymentLogDto paramMap);
	
	public int updatePaymentLog(DochaPaymentLogDto paramMap);

	public int updateReserveMaster(DochaMap paramMap);
	
	public int updateReserve(DochaMap paramMap);
	
	public List<DochaQuoteUserInfoDto> selectQuotePaymentSuccessList(DochaMap paramMap);
	
	public DochaQuoteUserInfoDto selectQuotePaymentSuccessDetail(DochaMap paramMap);
	
	public int updateCompliteQuoteRentCompany(DochaMap paramMap);
	
	public int updateNotChoiseQuoteRentCompany(DochaMap paramMap);

	public Map<String, Object> paymentCancel(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception;

	public void paymentCancelSchdule(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception;

	/**
	 * 
	 * @param paramMap 컨트롤러에서 전달할 파라미터
	 * @param url 아임포트 결제 검증 API URL
	 * @param impKey 아임포트 key
	 * @param impSecret 아임포트 시크릿 키
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 * @throws Exception
	 */
	public void paymentOne(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception;
	
	/**
	 * 
	 * @param paramMap 컨트롤러에서 전달할 파라미터
	 * @param url 아임포트 결제 검증 API URL
	 * @param impKey 아임포트 key
	 * @param impSecret 아임포트 시크릿 키
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 * @throws Exception
	 */
	public void paymentSchedule(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception;

	/**
	 * 고객 후기 등록
	 * @param paramMap 
	 * @param 
	 * @throws 
	 */
	public int insertUserReview(HttpServletRequest request, DochaMap param);
	
	/**
	 * 고객 후기 등록 여부
	 * @param paramMap 
	 * @param 
	 * @throws 
	 */
	public int selectMyReviewCnt(DochaMap paramMap);

}
