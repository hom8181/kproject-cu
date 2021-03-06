package com.ohdocha.cu.kprojectcu.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohdocha.cu.kprojectcu.domain.*;
import com.ohdocha.cu.kprojectcu.exception.BadRequestException;
import com.ohdocha.cu.kprojectcu.mapper.*;
import com.ohdocha.cu.kprojectcu.util.*;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import com.ohdocha.cu.kprojectcu.config.ErrorCode;
import com.ohdocha.cu.kprojectcu.config.Properties;
import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

@Service("payment")
@Slf4j
@AllArgsConstructor
@Transactional
public class DochaPaymentServiceImpl implements DochaPaymentService,ErrorCode {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String SEPARATION = "/";
    
    private final Properties properties;
    
    @Autowired
    private final DochaPaymentDao dao;

    @Autowired
    private final DochaCarSearchDao carSearchDao;

    @Autowired
    private final DochaScheduledDao scheduledDao;

    @Autowired
    private final DochaUserInfoDao userInfoDao;

    @Autowired
    private final DochaRentcarDao rentcarDao;

    @Autowired
    private final DochaAlarmTalkMsgUtil alarmTalk;

    @Autowired
    private final DochaUserReviewDao userReviewDao;
    
    @Override
    public int insertReserveMaster(DochaPaymentDto paramMap) {
        // TODO Auto-generated method stub
        return dao.insertReserveMaster(paramMap);
    }

    @Override
    public int insertReserve(DochaPaymentReserveDto paramMap) {
        // TODO Auto-generated method stub
        return dao.insertReserve(paramMap);
    }

    @Override
    public int insertPaymentDetail(DochaPaymentDetailDto paramMap) {
        // TODO Auto-generated method stub
        return dao.insertPaymentDetail(paramMap);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int insertPaymentLog(DochaPaymentLogDto paramMap) {
        // TODO Auto-generated method stub
        return dao.insertPaymentLog(paramMap);
    }

    @Override
    public int updatePaymentLog(DochaPaymentLogDto paramMap) {
        // TODO Auto-generated method stub
        return dao.updatePaymentLog(paramMap);
    }

    @Override
    public int updateReserveMaster(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.updateReserveMaster(paramMap);
    }

    @Override
    public int updateReserve(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.updateReserve(paramMap);
    }

    @Override
    public List<DochaQuoteUserInfoDto> selectQuotePaymentSuccessList(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.selectQuotePaymentSuccessList(paramMap);
    }

    @Override
    public DochaQuoteUserInfoDto selectQuotePaymentSuccessDetail(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.selectQuotePaymentSuccessDetail(paramMap);
    }

    @Override
    public int updateCompliteQuoteRentCompany(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.updateCompliteQuoteRentCompany(paramMap);
    }

    @Override
    public int updateNotChoiseQuoteRentCompany(DochaMap paramMap) {
        // TODO Auto-generated method stub
        return dao.updateNotChoiseQuoteRentCompany(paramMap);
    }

    @Override
    public Map<String, Object> paymentCancel(DochaMap paramMap, String url, String impKey, String impSecret) throws Exception {
        String accessToken = getAccessToken(impKey, impSecret, url);

        //헤더에 AccessToken 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessToken);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(connectImport(url + "/payments/cancel", headers, HttpMethod.POST, paramMap), Map.class);

        return resultMap;

    }

    @Override
    public void paymentCancelSchdule(DochaMap paramMap, String url, String impKey, String impSecret) throws Exception {
        String accessToken = getAccessToken(impKey, impSecret, url);

        //헤더에 AccessToken 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessToken);

        List<DochaScheduledDto> scheduledList = scheduledDao.selectScheduledListForCancel(paramMap);

        for (int i = 0; i < scheduledList.size(); i++) {
            paramMap.put("merchant_uid", scheduledList.get(i).getMerchantUid());
            paramMap.put("customer_uid", scheduledList.get(i).getCustomerUid());

            ObjectMapper mapper = new ObjectMapper();
            mapper.readValue(connectImport(url + "/subscribe/payments/unschedule", headers, HttpMethod.POST, paramMap), Map.class);

            scheduledDao.updateCancelScheduleStatus(paramMap);
        }

    }


    /**
     * 일반결제시 아임포트 API 호출 후 결제검증 후 주문저장
     */
    public void paymentOne(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception {
        String extensionRmIdx = null;

        // 연장 결제 인 경우
        if (paramMap.get("mode") != null) {
            extensionRmIdx = (String) paramMap.get("extensionRmIdx");
        }

        String rmIdx = KeyMaker.getInsetance().getKeyDeafult("RM");
        String reIdx = KeyMaker.getInsetance().getKeyDeafult("RE");
        String plIdx = KeyMaker.getInsetance().getKeyDeafult("PL");
        String pdIdx = KeyMaker.getInsetance().getKeyDeafult("PD");

        paramMap.put("rmIdx", rmIdx);
        paramMap.put("extensionRmIdx", extensionRmIdx);
        paramMap.put("reIdx", reIdx);
        paramMap.put("plIdx", plIdx);
        paramMap.put("pdIdx", pdIdx);

        //결제검증전문
        String orgMsg = null;
        //결제검증 결과
        Map<String, Object> result = null;
        //결제 중 paydata
        Map<String, Object> payData = null;
        //아임포트 결제 key값을 셋팅
        String impUid = paramMap.getString("impUid");
        //결제검증을 위해 아임포트 AccessToken 발급
        String accessToken = getAccessToken(impKey, impSecret, url);
        //아임포트 결제 검증 호출부분
        try {
            //아임포트 AccessToken, 결제 key값을 전달하여 결제데이터 호출
            result = getPaymentInfo(impUid, accessToken, url);

            //결제전문 중 결제관련한 데이터를 가져옴
            payData = (Map<String, Object>) result.get("response");

            //결제전문을 JSONString형태로 변환
            ObjectMapper mapper = new ObjectMapper();
            orgMsg = mapper.writeValueAsString(result);
        } catch (Exception e) {
            //에러발생시 로그처리 후 에러 throws
            logger.error("Import Connect Error", e);
            throw e;
        }

        //세션에 저장했던 결제 전 호출된 금액 및 차량정보
        DochaCarInfoDto resCarInfo = (DochaCarInfoDto) paramMap.get("resCarDto");
        //결제유저정보
        DochaUserInfoDto userInfo = (DochaUserInfoDto) paramMap.get("user");

        //세션의 일별요금, 보험요금을 불러옴
        String sessionDailyStandardPay = resCarInfo.getDailyStandardPay();
        String sessionInsuranceFee = resCarInfo.getInsuranceFee();

        sessionDailyStandardPay = sessionDailyStandardPay == null ? "0" : sessionDailyStandardPay;
        sessionInsuranceFee = sessionInsuranceFee == null ? "0" : sessionInsuranceFee;

        int dailyStandardPay = Integer.parseInt(sessionDailyStandardPay);
        int insuranceFee = Integer.parseInt(sessionInsuranceFee);

        //결제검증 데이터 중 결제금액 가져옴
        int payment = (int) payData.get("amount");
        //결제검증 데이터 중 승인번호 가져옴
        String applyNum = (String) payData.getOrDefault("apply_num", null);

        //결제금액이 세션금액과 일치하지 않는경우
//		if(payment != dailyStandardPay + insuranceFee) {
//
//			//paylog 저장 후 Exception throws
//			DochaPaymentLogDto payLog = new DochaPaymentLogDto();
//			payLog.setRmIdx(rmIdx);
//			payLog.setApprovalNumber(applyNum);
//			payLog.setPaymentAmount(Integer.toString(payment));
//			payLog.setOrgMsg(orgMsg);
//			payLog.setApprovalYn(applyNum == null ? "N" : "Y");
//			payLog.setPaymentRequestAmount(Integer.toString(dailyStandardPay + insuranceFee));
//			payLog.setPlIdx(plIdx);
//			payLog.setPdIdx(pdIdx);
//			dao.insertPaymentLog(payLog);
//
//			throw new Exception("Payment Amount Check Error");
//		}

        //주문저장처리
        paymentSave(paramMap, orgMsg, result, payData);

    }

    /**
     * 아임포트 API에 스케쥴 등록 후 주문저장
     */
    public void paymentSchedule(DochaMap paramMap, String url, String impKey, String impSecret) throws JsonMappingException, JsonProcessingException, Exception {
        String extensionRmIdx = null;
        if (paramMap.get("mode") != null) {
            extensionRmIdx = (String) paramMap.get("extensionRmIdx");
        }
        String rmIdx = KeyMaker.getInsetance().getKeyDeafult("RM");
        String reIdx = KeyMaker.getInsetance().getKeyDeafult("RE");
        String plIdx = KeyMaker.getInsetance().getKeyDeafult("PL");
        String pdIdx = KeyMaker.getInsetance().getKeyDeafult("PD");

        paramMap.put("rmIdx", rmIdx);
        paramMap.put("extensionRmIdx", extensionRmIdx);
        paramMap.put("reIdx", reIdx);
        paramMap.put("plIdx", plIdx);
        paramMap.put("pdIdx", pdIdx);

        //결제검증전문
        String orgMsg = null;
        //결제검증 결과
        Map<String, Object> result = null;
        //결제 중 paydata
        Map<String, Object> payData = null;

        //세션에 저장했던 결제 전 호출된 금액 및 차량정보
        DochaCarInfoDto resCarInfo = (DochaCarInfoDto) paramMap.get("resCarDto");
        //결제유저정보
        DochaUserInfoDto userInfo = (DochaUserInfoDto) paramMap.get("user");

        // 결제개월수 설정
        int month = paramMap.getInt("rentMonth");
        int mmRentFee = paramMap.getInt("mmRentFee");
        int mmLastRentFee = paramMap.getInt("mmLastRentFee");

        int count = 1;


        // 마지막 월 결제 요금이 있으면 month + 1
        if (mmLastRentFee > 0) {
            month++;
        }

        String rentStartDt = paramMap.getString("rentStartDt");

        //개월수만큼 결제할 스케쥴 시간을 생성
        List<Long> scheduleTime = getUnixTimeArray(month, rentStartDt, paramMap);

        //아임포트 API를 통해 저장할 스케쥴 파라미터 생성
        HashMap<String, Object> schedule = new HashMap<String, Object>();

        //payment.html에서 아임포트 빌링키 발급. 발급 시 유니크한 key를 아임포트 API 호출 시 customer_uid 파라미터에 넣어서 전송하면,
        //아임포트에서 해당 key와 결제 빌링키를 매칭해서 보관. 실제 사용할때는 빌링키 발급 시 customer_uid에 넣은 유니크key를 전송하여 사용.
        //생성 시 회웡 idx(UR_IDX)를 넣어서 빌링키 발급, 해당 값을 전송하여 빌링키 결제에 사용
        schedule.put("customer_uid", userInfo.getUrIdx());
        schedule.put("checking_amount", 0);

        //결제할 스케쥴 시간과, 나머지 필요 파라미터를 추가하여 아임포트에 전송하여 정기결제 스케쥴을 생성
        List<Map<String, Object>> scheduleList = new ArrayList<Map<String, Object>>();

        for (Long unixTime : scheduleTime) {
            Map<String, Object> scheduleInfo = new HashMap<String, Object>();

            scheduleInfo.put("rmIdx", rmIdx); //결제금액
            scheduleInfo.put("merchant_uid", userInfo.getUrIdx() + unixTime + new Date().getTime());//유니크한 주문번호가 필요하므로, uridx+결제예정시간으로 유니크 키 생성
            scheduleInfo.put("schedule_at", unixTime); //결제 할 스케쥴 시간(uinxtime)
            scheduleInfo.put("amount", mmRentFee); //결제금액
            scheduleInfo.put("name", resCarInfo.getModelName() + " " + resCarInfo.getModelDetailName()); //상품명
            scheduleInfo.put("buyer_name", userInfo.getUsername()); //주문자명
            scheduleInfo.put("buyer_tel", userInfo.getUserContact1()); //주문자 연락처
            scheduleInfo.put("buyer_addr", userInfo.getUserAddress() + " " + userInfo.getUserAddressDetail()); //주문자 주소
            scheduleInfo.put("buyer_postcode", userInfo.getUserZipCode()); //주문자 우편번호
            scheduleInfo.put("payCount", count); // 회차 정보
            scheduleInfo.put("totalPayCount", scheduleTime.size()); // 총 회차
            scheduleInfo.put("customer_uid", userInfo.getUrIdx()); // 고객 idx

            if (mmLastRentFee > 0 && count == month) {
                scheduleInfo.put("amount", mmLastRentFee); //마지막 결제 금액
            }

            count++;

            scheduledDao.insertPaymentSchedule(scheduleInfo);

            scheduleList.add(scheduleInfo);

        }

        //생성한 정기결제 스케쥴 목록을 파라미터에 담음
        schedule.put("schedules", scheduleList);

        //아임포트 결제 key값을 셋팅
        String impUid = paramMap.getString("impUid");
        //결제검증을 위해 아임포트 AccessToken 발급
        String accessToken = getAccessToken(impKey, impSecret, url);
        //아임포트 결제 검증 호출부분
        try {
            //아임포트 AccessToken, 결제 key값을 전달하여 정기결제데이터 저장
            result = postPaymentInfo(impUid, accessToken, url, schedule);

            //결제전문 중 결제관련한 데이터를 가져옴
            List<Map<String, Object>> payDayaList = (List<Map<String, Object>>) result.get("response");

            payData = payDayaList.get(0);

            //결제전문을 JSONString형태로 변환
            ObjectMapper mapper = new ObjectMapper();
            orgMsg = mapper.writeValueAsString(result);
        } catch (Exception e) {
            //에러발생시 로그처리 후 에러 throws
            logger.error("Import Connect Error", e);
            throw e;
        } finally {
			/*
			HashMap<String, Object> cancelInfo = new HashMap<String, Object>();
			cancelInfo.put("imp_uid", paramMap.getString("imp_uid"));
			cancelInfo.put("reason", "빌링키 발급 시 결제한 금액을 취소");
			cancelPayment(impUid, accessToken, url, cancelInfo);
			*/
        }

        paymentSave(paramMap, orgMsg, result, payData);

    }

    private void paymentSave(DochaMap paramMap, String orgMsg, Map<String, Object> result, Map<String, Object> payData) throws Exception {

        //DB 저장 전 각 테이블의 key값 생성
        String rmIdx = paramMap.getString("rmIdx");
        String reIdx = paramMap.getString("reIdx");
        String plIdx = paramMap.getString("plIdx");
        String pdIdx = paramMap.getString("pdIdx");


        //결제검증 오류시 취소처리를 위해 Exception 저장
        Exception payServiceException = null;

        //세션에 저장했던 결제 전 호출된 금액 및 차량정보
        DochaCarInfoDto resCarInfo = (DochaCarInfoDto) paramMap.get("resCarDto");
        //결제유저정보
        DochaUserInfoDto userInfo = (DochaUserInfoDto) paramMap.get("user");

        //세션의 일별요금, 보험요금을 불러옴
        String sessionDailyStandardPay;
        String sessionInsuranceFee;
        String sessionDeliveryFee;
        String rentStartDt = null;
        String rentStartTime = null;

        sessionDailyStandardPay = paramMap.getString("calcDisRentFee") == null ? "0" : paramMap.getString("calcDisRentFee");
        sessionInsuranceFee = paramMap.getString("insuranceFee") == null ? "0" : paramMap.getString("insuranceFee");
        sessionDeliveryFee = paramMap.getString("deliveryFee") == null ? "0" : paramMap.getString("deliveryFee");

        int dailyStandardPay = Integer.parseInt(sessionDailyStandardPay);
        int insuranceFee = Integer.parseInt(sessionInsuranceFee);
        int deliveryFee = Integer.parseInt(sessionDeliveryFee);
        String totalFee = Integer.toString(dailyStandardPay + insuranceFee + deliveryFee);

        String rentStartString = paramMap.getString("rentStartDt");
        String rentEndString = paramMap.getString("rentEndDt");

        rentStartDt = rentStartString.substring(0, 4) + "-" + rentStartString.substring(4, 6) + "-" + rentStartString.substring(6, 8);
        rentStartTime = rentStartString.substring(8, 10) + ":" + rentStartString.substring(10, 12);

        String rentEndDt = rentEndString.substring(0, 4) + "-" + rentEndString.substring(4, 6) + "-" + rentEndString.substring(6, 8);
        String rentEndTime = rentEndString.substring(8, 10) + ":" + rentEndString.substring(10, 12);

        String periodDt = paramMap.getString("periodDt");

        String rentFee = paramMap.getString("rentFee");
        String disRentFee = paramMap.getString("calcDisRentFee");
        String disCountFee = Integer.toString(Integer.parseInt(rentFee) - Integer.parseInt(disRentFee));
        String deliveryTypeCode = paramMap.getString("deliveryTypeCode");
        String carDamageCover = paramMap.getString("carDamageCover");
        String carDamageNumber = paramMap.getString("carDamageNumber");

        int mmRentFee = Integer.parseInt(paramMap.getString("mmRentFee"));
        String longTermYn = "ST";
        if (mmRentFee > 0) {
            longTermYn = "LT";
        }


        //결제검증 데이터 중 결제금액 가져옴
        int payment = (int) payData.get("amount");
        String merchantUid = (String) payData.get("merchant_uid");
        int balance = Integer.parseInt(totalFee) - (int) payData.get("amount");
        int ceilMonth = paramMap.getInt("ceilMonth");

        //결제검증 데이터 중 승인번호 가져옴
        String applyNum = (String) payData.getOrDefault("apply_num", null);

        DochaUserInfoDto licenseInfo = userInfoDao.selectLicenseInfo(userInfo);


        DochaPaymentDto paymentDto = new DochaPaymentDto();
        paymentDto.setRmIdx(rmIdx);
        //주문 및 결제데이터 저장
        try {
            // 예약 시에는 예약 테이블 insert
            if (paramMap.get("mode") == null) {
                // 차량 정보 관련
                paymentDto.setCompanyName(resCarInfo.getCompanyName());
                paymentDto.setReserveStatusCode("예약");
                paymentDto.setRentStartDay(rentStartDt);
                paymentDto.setRentStartTime(rentStartTime);
                paymentDto.setRentEndDay(rentEndDt);
                paymentDto.setRentEndTime(rentEndTime);
                paymentDto.setPeriodDt(periodDt);
                paymentDto.setDeliveryTypeCode(deliveryTypeCode);
                paymentDto.setLongTermYn(longTermYn);

                // 유저 관련
                paymentDto.setReserveUserName(userInfo.getUserName());
                paymentDto.setReserveUserContact1(userInfo.getUserContact1());
                paymentDto.setReserveUserBirthday(userInfo.getUserBirthday());

                paymentDto.setFirstDriverBirthday(userInfo.getUserBirthday());
                paymentDto.setFirstDriverName(userInfo.getUserName());
                paymentDto.setFirstDriverContact(userInfo.getUserContact1());

                // 면허 관련
                paymentDto.setFirstDriverLicenseCode(licenseInfo.getLicenseCode());
                paymentDto.setFirstDriverLicenseNumber(licenseInfo.getLicenseNumber());
                paymentDto.setFirstDriverLicenseExpirationDate(licenseInfo.getLicenseExpiration());
                paymentDto.setFirstDriverLicenseIsDate(licenseInfo.getLicenseIssueDt());

                if (paramMap.get("secondLicenseInfo") != null ) {
                    DochaPaymentDto secondDriverInfo = (DochaPaymentDto) paramMap.get("secondLicenseInfo");

                    paymentDto.setSecondDriverName(secondDriverInfo.getSecondDriverName());
                    paymentDto.setSecondDriverContact(secondDriverInfo.getSecondDriverContact());
                    paymentDto.setSecondDriverBirthday(secondDriverInfo.getSecondDriverBirthday());

                    paymentDto.setSecondDriverLicenseCode(secondDriverInfo.getSecondDriverLicenseCode());
                    paymentDto.setSecondDriverLicenseNumber(secondDriverInfo.getSecondDriverLicenseNumber());
                    paymentDto.setSecondDriverExpirationDate(secondDriverInfo.getSecondDriverExpirationDate());
                    paymentDto.setSecondDriverLicenseDate(secondDriverInfo.getSecondDriverLicenseDate());

                }

                // 보험 관련
                paymentDto.setCarDamageCover(carDamageCover);
                paymentDto.setCarDamageNumber(carDamageNumber);
                paymentDto.setDeliveryAddr(paramMap.getString("myLocation"));
                paymentDto.setReturnAddr(paramMap.getString("myLocation"));
                paymentDto.setReturnAddr(paramMap.getString("myLocation"));


                // 가격 관련
                paymentDto.setRentFee(paramMap.getString("rentFee"));
                paymentDto.setCarDeposit(paramMap.getString("deposit"));
                paymentDto.setDiscountFee(disCountFee);
                paymentDto.setInsuranceFee(sessionInsuranceFee);
                paymentDto.setDeliveryFee(paramMap.getString("deliveryFee"));
                paymentDto.setTotalFee(totalFee);

                paymentDto.setCrIdx(resCarInfo.getCrIdx());
                paymentDto.setRtIdx(resCarInfo.getRtIdx());
                paymentDto.setCarTypeCode(resCarInfo.getCartypeCode());
                paymentDto.setUrIdx(userInfo.getUrIdx());
                paymentDto.setUlIdx1(userInfo.getUlIdx());

                // 결제 데이터
                paymentDto.setPaymentTotalAmount(totalFee);
                paymentDto.setSumPaymentAmount(Integer.toString(payment));
                paymentDto.setBalance(balance);
                paymentDto.setPayCount(1);
                paymentDto.setTotalPayCount(1);
                paymentDto.setMerchantUid(merchantUid);

                if (longTermYn.equals("ST")) {
                    paymentDto.setImpUid((String) payData.get("imp_uid"));
                    paymentDto.setReceiptUrl((String) payData.get("receipt_url"));
                } else {
                    paymentDto.setNextPaymentDay("정기결제 전");
                    paymentDto.setMonthlyFee(Integer.toString(payment));
                    paymentDto.setSumPaymentAmount("0");
                    paymentDto.setBalance(Integer.parseInt(totalFee));
                    paymentDto.setPayCount(0);
                    paymentDto.setTotalPayCount(ceilMonth);

                    if (totalFee.equals(Integer.toString(payment))) {
                        paymentDto.setTotalPayCount(1);
                        paymentDto.setSumPaymentAmount(Integer.toString(payment));
                        paymentDto.setBalance(0);
                        paymentDto.setPayCount(1);
                        paymentDto.setImpUid((String) payData.get("imp_uid"));
                        paymentDto.setReceiptUrl((String) payData.get("receipt_url"));
                    }
                }

                dao.insertReserveMaster(paymentDto);


                // 차량 상태 업데이트 ( RESERVE_ABLE_YN = N )
//            paramMap.put("reserveAbleYn", "N");
                paramMap.put("carStatusCode", "예약중");
                paramMap.put("crIdx", resCarInfo.getCrIdx());
                paramMap.put("rtIdx", resCarInfo.getRtIdx());
                carSearchDao.updateDcCarInfo(paramMap);


            }
            // 연장 결제 인 경우에는 부모 rmIdx 를 넣어서 insert
            else {
                String extensionRmIdx = paramMap.getString("extensionRmIdx");
                paramMap.put("rmIdx", extensionRmIdx);

                List<DochaPaymentDto> reserveInfoList = dao.selectReserveInfo(paramMap);
                DochaPaymentDto reserveInfo = reserveInfoList.get(0);
                paramMap.put("rmIdx", rmIdx);

                // 연장 결제 관련
                paymentDto.setExtensionRmIdx(extensionRmIdx);
                paymentDto.setExtensionYn("Y");
                paymentDto.setRmIdx(rmIdx);

                // 차량 정보 관련
                paymentDto.setCompanyName(reserveInfo.getCompanyName());
                paymentDto.setReserveStatusCode("예약");
                paymentDto.setRentStartDay(rentStartDt);
                paymentDto.setRentStartTime(rentStartTime);
                paymentDto.setRentEndDay(rentEndDt);
                paymentDto.setRentEndTime(rentEndTime);
                paymentDto.setPeriodDt(periodDt);
                paymentDto.setDeliveryTypeCode(reserveInfo.getDeliveryTypeCode());
                paymentDto.setLongTermYn(longTermYn);

                // 유저 관련
                paymentDto.setReserveUserName(userInfo.getUserName());
                paymentDto.setReserveUserContact1(userInfo.getUserContact1());
                paymentDto.setReserveUserBirthday(userInfo.getUserBirthday());

                paymentDto.setFirstDriverBirthday(userInfo.getUserBirthday());
                paymentDto.setFirstDriverName(userInfo.getUserName());
                paymentDto.setFirstDriverContact(userInfo.getUserContact1());
                paymentDto.setFirstDriverBirthday(userInfo.getUserBirthday());

                // 면허 관련
                paymentDto.setFirstDriverLicenseCode(licenseInfo.getLicenseCode());
                paymentDto.setFirstDriverLicenseNumber(licenseInfo.getLicenseNumber());
                paymentDto.setFirstDriverLicenseExpirationDate(licenseInfo.getLicenseExpiration());
                paymentDto.setFirstDriverLicenseIsDate(licenseInfo.getLicenseIssueDt());

                paymentDto.setSecondDriverName("");
                paymentDto.setSecondDriverContact("");
                paymentDto.setSecondDriverBirthday("");

                // 보험 관련
                paymentDto.setCarDamageCover(carDamageCover);
                paymentDto.setCarDamageNumber(carDamageNumber);
                paymentDto.setDeliveryAddr(reserveInfo.getDeliveryAddr());
                paymentDto.setReturnAddr(reserveInfo.getReturnAddr());


                // 가격 관련
                paymentDto.setRentFee(paramMap.getString("rentFee"));
                paymentDto.setCarDeposit(reserveInfo.getCarDeposit());
                paymentDto.setDiscountFee(disCountFee);
                paymentDto.setInsuranceFee(sessionInsuranceFee);
                paymentDto.setDeliveryFee(paramMap.getString("deliveryFee"));
                paymentDto.setTotalFee(totalFee);

                paymentDto.setCrIdx(resCarInfo.getCrIdx());
                paymentDto.setRtIdx(resCarInfo.getRtIdx());
                paymentDto.setCarTypeCode(resCarInfo.getCartypeCode());
                paymentDto.setUrIdx(userInfo.getUrIdx());
                paymentDto.setUlIdx1(userInfo.getUlIdx());

                // 결제 데이터
                paymentDto.setPaymentTotalAmount(totalFee);
                paymentDto.setSumPaymentAmount(Integer.toString(payment));
                paymentDto.setBalance(balance);
                paymentDto.setPayCount(1);
                paymentDto.setTotalPayCount(1);
                paymentDto.setMerchantUid(merchantUid);

                if (longTermYn.equals("ST")) {
                    paymentDto.setImpUid((String) payData.get("imp_uid"));
                    paymentDto.setReceiptUrl((String) payData.get("receipt_url"));
                } else {
                    paymentDto.setNextPaymentDay("정기결제 전");
                    paymentDto.setMonthlyFee(Integer.toString(payment));
                    paymentDto.setSumPaymentAmount("0");
                    paymentDto.setBalance(Integer.parseInt(totalFee));
                    paymentDto.setPayCount(0);
                    paymentDto.setTotalPayCount(ceilMonth);

                    if (totalFee.equals(Integer.toString(payment))) {
                        paymentDto.setTotalPayCount(1);
                        paymentDto.setSumPaymentAmount(Integer.toString(payment));
                        paymentDto.setBalance(0);
                        paymentDto.setPayCount(1);
                        paymentDto.setImpUid((String) payData.get("imp_uid"));
                        paymentDto.setReceiptUrl((String) payData.get("receipt_url"));
                    }
                }

                dao.insertReserveMasterForExtension(paymentDto);


                // 차량 상태 업데이트 ( RESERVE_ABLE_YN = N )
//            paramMap.put("reserveAbleYn", "N");
                paramMap.put("carStatusCode", "예약중");
                paramMap.put("crIdx", resCarInfo.getCrIdx());
                paramMap.put("rtIdx", resCarInfo.getRtIdx());
                carSearchDao.updateDcCarInfo(paramMap);
            }


            //ReserveLog 저장 (현재 필수정보만 셋팅, 비지니스 로직에 따라 데이터 추가필요)
            DochaPaymentReserveDto paymentReserveDto = new DochaPaymentReserveDto();

            paymentReserveDto.setReIdx(reIdx);
            paymentReserveDto.setRmIdx(rmIdx);
            paymentReserveDto.setRentFee(sessionDailyStandardPay);
            paymentReserveDto.setInsuranceFee(sessionInsuranceFee);
            paymentReserveDto.setReserveStatusCode("예약");

            dao.insertReserve(paymentReserveDto);

            //PaymentDetail저장 (현재 필수정보만 셋팅, 비지니스 로직에 따라 데이터 추가필요)
            DochaPaymentDetailDto paymentDetailDto = new DochaPaymentDetailDto();
            paymentDetailDto.setApprovalNumber(applyNum);
            paymentDetailDto.setPaymentAmount(Integer.toString(payment));
            paymentDetailDto.setPgCode((String) payData.get("pg_id"));
            paymentDetailDto.setPaymentKindCode((String) payData.get("card_name"));
            paymentDetailDto.setPaymentTypeCode((String) payData.get("pay_method"));
            paymentDetailDto.setRmIdx(rmIdx);
            paymentDetailDto.setReIdx(reIdx);
            paymentDetailDto.setUrIdx(userInfo.getUrIdx());
            paymentDetailDto.setPlIdx(plIdx);
            paymentDetailDto.setPdIdx(pdIdx);

            dao.insertPaymentDetail(paymentDetailDto);

            // 고객에게 알림톡 전송
            try {
                String nowDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String nowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                DecimalFormat numberFormat = new DecimalFormat("###,###");

                DochaAlarmTalkDto dto = new DochaAlarmTalkDto();

                if (deliveryTypeCode.equals("OF")) {
                    dto.setDeliveryTypeCode("지점방문");//대여방법
                } else {
                    dto.setDeliveryTypeCode("배달대여");//대여방법
                }

                dto.setDeliveryTypeCode("지점방문");//대여방법
                dto.setInsurancecopayment(numberFormat.format(Integer.parseInt(sessionInsuranceFee))); //보험료
                dto.setRentAmount(numberFormat.format(Integer.parseInt(rentFee))); //대여료
                dto.setDiscountAmount(numberFormat.format(Integer.parseInt(disCountFee))); //할인료
                dto.setPayAmount(numberFormat.format(Integer.parseInt(totalFee)) + "원");//총결제금액
                dto.setCarDeposit(numberFormat.format(Integer.parseInt(paramMap.getString("deposit"))));//보증금

                dto.setBookDate(nowDate + "(" + Util.getWeekByString(nowDate, "yyyy-MM-dd") + ") " + nowTime); //예약일
                dto.setRentDate(rentStartDt + "(" + Util.getWeekByString(rentStartDt, "yyyy-MM-dd") + ") " + rentStartTime); //렌트시작일
                dto.setReturnDate(rentEndDt + "(" + Util.getWeekByString(rentEndDt, "yyyy-MM-dd") + ") " + rentEndTime); //렌트종료일
                dto.setCarName(resCarInfo.getYear() + " " + resCarInfo.getModelName() + " " + resCarInfo.getModelDetailName()); //차량명

                dto.setCompanyName(resCarInfo.getCompanyName() + " " + resCarInfo.getBranchName());//대여점명
                dto.setCompanyContact(resCarInfo.getCompanyContact1());//대여점 연락처
                dto.setCompanyAddr(resCarInfo.getCompanyAddress());//대여점 위치
                dto.setRentAddr(resCarInfo.getCompanyAddress());//대여위치
                dto.setReturnAddr(resCarInfo.getCompanyAddress());//반납위치
                dto.setPhone(userInfo.getUserContact1());//알림톡 전송할 번호

                if (ceilMonth > 1 && payment != Integer.parseInt(totalFee)) {
                    dto.setPayAmount(numberFormat.format(payment) + "원X" + ceilMonth + "개월");//총결제금액

                    if (Integer.parseInt(totalFee) % payment != 0) {
                        dto.setPayAmount(numberFormat.format(payment) + "원X" + (ceilMonth - 1) + "개월+마지막 월 " + numberFormat.format(Integer.parseInt(totalFee) % payment) + "원");//총결제금액
                    }
                }


                //알림톡발송
                // (1) 일대여 / 지점방문
                if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("OF")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000001.getCode());
                }

                // (2) 월대여 / 지점 방문
                else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("OF")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000002.getCode());
                }

                // (3) 일대여 / 배달 대여
                else if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("DL")) {
                    dto.setDeliveryTypeCode("배달대여" + " (배달료 " + numberFormat.format(deliveryFee) + ")원 포함");//대여방법
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000003.getCode());
                }

                // (4) 월대여 / 배달 대여
                else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("DL")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000004.getCode());
                }


                //알림 톡 발송 후 로깅
                HttpResponse<JsonNode> response = alarmTalk.sendAlramTalk(dto);
                if (response.getStatus() == 200) {
                    logger.info("AlarmTalk Send Compelite");
                    logger.info(response.getBody().toPrettyString());
                } else {
                    logger.info("AlarmTalk Send Fail");
                    logger.error(response.getBody().toPrettyString());
                }
            } catch (Exception ex) {
                //알림톡 발송을 실패하더라도 오류발생시키지 않고 결제처리 완료를 위해 오류는 catch에서 로깅처리만 함
                logger.error("Error", ex);
            }

            // 관리자들 에게 알림톡 전송
            List<DochaRentCompanyDto> rentCompanyDtoList = rentcarDao.selectCompanyContactListForAlarmTalk(paramMap);

            for (int i = 0; i < rentCompanyDtoList.size(); i++) {
                try {
                    String nowDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String nowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                    DecimalFormat numberFormat = new DecimalFormat("###,###");

                    DochaAlarmTalkDto dto = new DochaAlarmTalkDto();

                    if (deliveryTypeCode.equals("OF")) {
                        dto.setDeliveryTypeCode("지점방문");//대여방법
                    } else {
                        dto.setDeliveryTypeCode("배달대여");//대여방법
                    }
                    dto.setBookDate(nowDate + "(" + Util.getWeekByString(nowDate, "yyyy-MM-dd") + ") " + nowTime); //예약일
                    dto.setRentDate(rentStartDt + "(" + Util.getWeekByString(rentStartDt, "yyyy-MM-dd") + ") " + rentStartTime); //렌트시작일
                    dto.setReturnDate(rentEndDt + "(" + Util.getWeekByString(rentEndDt, "yyyy-MM-dd") + ") " + rentEndTime); //렌트종료일
                    dto.setPeriodDt(paramMap.getString("periodDt")); // 대여기간

                    dto.setCarName(resCarInfo.getYear() + " " + resCarInfo.getModelName() + " " + resCarInfo.getModelDetailName()); //차량명
                    dto.setCarNumber(resCarInfo.getCarNumber()); //차량번호

                    dto.setInsurancecopayment(numberFormat.format(Integer.parseInt(sessionInsuranceFee))); //보험료
                    dto.setRentAmount(numberFormat.format(Integer.parseInt(rentFee))); //대여료
                    dto.setDiscountAmount(numberFormat.format(Integer.parseInt(disCountFee))); //할인료
                    dto.setPayAmount(numberFormat.format(Integer.parseInt(totalFee)) + "원");//총결제금액
                    dto.setCarDeposit(numberFormat.format(Integer.parseInt(paramMap.getString("deposit"))));//보증금

                    dto.setRentAddr(paramMap.getString("myLocation"));//대여위치
                    dto.setReturnAddr(paramMap.getString("myLocation"));//반납위치

                    dto.setUserName(userInfo.getUserName());          // 예약자 이름
                    dto.setUserContact(userInfo.getUserContact1().substring(0, 3) + "-" + userInfo.getUserContact1().substring(3, 7) + "-" + userInfo.getUserContact1().substring(7, 11));       // 예약자 번호
                    dto.setUserBirthday(userInfo.getUserBirthday());      // 예약자 생년월일
                    dto.setUserDriverLience(licenseInfo.getLicenseCode());  // 예약자 면허종류

                    dto.setPhone(rentCompanyDtoList.get(i).getCompanyContact1());//알림톡 전송할 번호

                    if (ceilMonth > 1 && payment != Integer.parseInt(totalFee)) {
                        dto.setPayAmount(numberFormat.format(payment) + "원X" + ceilMonth + "개월");//총결제금액

                        if (Integer.parseInt(totalFee) % payment != 0) {
                            dto.setPayAmount(numberFormat.format(payment) + "원X" + (ceilMonth - 1) + "개월+마지막 월 " + numberFormat.format(Integer.parseInt(totalFee) % payment) + "원");//총결제금액
                        }
                    }

                    //알림톡발송
                    // (1) 일대여 / 지점방문
                    if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("OF")) {
                        dto.setTemplateCode(DochaTemplateCodeProvider.A000012.getCode());
                    }

                    // (2) 월대여 / 지점 방문
                    else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("OF")) {
                        dto.setTemplateCode(DochaTemplateCodeProvider.A000013.getCode());

                    }

                    // (3) 일대여 / 배달 대여
                    else if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("DL")) {
                        dto.setDeliveryTypeCode("배달대여" + " (배달료 " + numberFormat.format(deliveryFee) + ")원 포함");//대여방법
                        dto.setTemplateCode(DochaTemplateCodeProvider.A000014.getCode());
                    }

                    // (4) 월대여 / 배달 대여
                    else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("DL")) {
                        dto.setTemplateCode(DochaTemplateCodeProvider.A000015.getCode());
                    }


                    //알림 톡 발송 후 로깅
                    HttpResponse<JsonNode> response = alarmTalk.sendAlramTalk(dto);
                    if (response.getStatus() == 200) {
                        logger.info("AlarmTalk Send Compelite");
                        logger.info(response.getBody().toPrettyString());
                    } else {
                        logger.info("AlarmTalk Send Fail");
                        logger.error(response.getBody().toPrettyString());
                    }

                } catch (Exception ex) {
                    //알림톡 발송을 실패하더라도 오류발생시키지 않고 결제처리 완료를 위해 오류는 catch에서 로깅처리만 함
                    logger.error("Error", ex);
                }
            }
//            ===========회사 이강구 카톡 추가====================
            try {
                String nowDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String nowTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                String testphone = "01099477228";

                DecimalFormat numberFormat = new DecimalFormat("###,###");

                DochaAlarmTalkDto dto = new DochaAlarmTalkDto();

                if (deliveryTypeCode.equals("OF")) {
                    dto.setDeliveryTypeCode("지점방문");//대여방법
                } else {
                    dto.setDeliveryTypeCode("배달대여");//대여방법
                }
                dto.setBookDate(nowDate + "(" + Util.getWeekByString(nowDate, "yyyy-MM-dd") + ") " + nowTime); //예약일
                dto.setRentDate(rentStartDt + "(" + Util.getWeekByString(rentStartDt, "yyyy-MM-dd") + ") " + rentStartTime); //렌트시작일
                dto.setReturnDate(rentEndDt + "(" + Util.getWeekByString(rentEndDt, "yyyy-MM-dd") + ") " + rentEndTime); //렌트종료일
                dto.setPeriodDt(paramMap.getString("periodDt")); // 대여기간

                dto.setCarName(resCarInfo.getYear() + " " + resCarInfo.getModelName() + " " + resCarInfo.getModelDetailName()); //차량명
                dto.setCarNumber(resCarInfo.getCarNumber()); //차량번호

                dto.setInsurancecopayment(numberFormat.format(Integer.parseInt(sessionInsuranceFee))); //보험료
                dto.setRentAmount(numberFormat.format(Integer.parseInt(rentFee))); //대여료
                dto.setDiscountAmount(numberFormat.format(Integer.parseInt(disCountFee))); //할인료
                dto.setPayAmount(numberFormat.format(Integer.parseInt(totalFee)) + "원");//총결제금액
                dto.setCarDeposit(numberFormat.format(Integer.parseInt(paramMap.getString("deposit"))));//보증금

                dto.setRentAddr(paramMap.getString("myLocation"));//대여위치
                dto.setReturnAddr(paramMap.getString("myLocation"));//반납위치

                dto.setUserName(userInfo.getUserName());          // 예약자 이름
                dto.setUserContact(userInfo.getUserContact1().substring(0, 3) + "-" + userInfo.getUserContact1().substring(3, 7) + "-" + userInfo.getUserContact1().substring(7, 11));       // 예약자 번호
                dto.setUserBirthday(userInfo.getUserBirthday());      // 예약자 생년월일
                dto.setUserDriverLience(licenseInfo.getLicenseCode());  // 예약자 면허종류

                dto.setPhone(testphone);//알림톡 전송할 번호

                if (ceilMonth > 1 && payment != Integer.parseInt(totalFee)) {
                    dto.setPayAmount(numberFormat.format(payment) + "원X" + ceilMonth + "개월");//총결제금액

                    if (Integer.parseInt(totalFee) % payment != 0) {
                        dto.setPayAmount(numberFormat.format(payment) + "원X" + (ceilMonth - 1) + "개월+마지막 월 " + numberFormat.format(Integer.parseInt(totalFee) % payment) + "원");//총결제금액
                    }
                }

                //알림톡발송
                // (1) 일대여 / 지점방문
                if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("OF")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000012.getCode());
                }

                // (2) 월대여 / 지점 방문
                else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("OF")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000013.getCode());

                }

                // (3) 일대여 / 배달 대여
                else if (paymentDto.getLongTermYn().equals("ST") && deliveryTypeCode.equals("DL")) {
                    dto.setDeliveryTypeCode("배달대여" + " (배달료 " + numberFormat.format(deliveryFee) + ")원 포함");//대여방법
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000014.getCode());
                }

                // (4) 월대여 / 배달 대여
                else if (paymentDto.getLongTermYn().equals("LT") && deliveryTypeCode.equals("DL")) {
                    dto.setTemplateCode(DochaTemplateCodeProvider.A000015.getCode());
                }


                //알림 톡 발송 후 로깅
                HttpResponse<JsonNode> response = alarmTalk.sendAlramTalk(dto);
                if (response.getStatus() == 200) {
                    logger.info("AlarmTalk Send Compelite");
                    logger.info(response.getBody().toPrettyString());
                } else {
                    logger.info("AlarmTalk Send Fail");
                    logger.error(response.getBody().toPrettyString());
                }

            } catch (Exception ex) {
                //알림톡 발송을 실패하더라도 오류발생시키지 않고 결제처리 완료를 위해 오류는 catch에서 로깅처리만 함
                logger.error("Error", ex);
            }

//            ===========회사 이강구 카톡 추가====================

        } catch (Exception e) {
            //오류발생시 로그처리 후 Exception throws
            logger.error("Payment Save Error", e);
            payServiceException = e;
            throw e;

        } finally {
            if (!(ceilMonth > 1 && payment != Integer.parseInt(totalFee))) {
                Calendar calendar = GregorianCalendar.getInstance();
                if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY)
                    calendar.set(Calendar.WEEK_OF_YEAR, calendar.get(Calendar.WEEK_OF_YEAR) + 1);
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

                long accountExpMillis = calendar.getTimeInMillis();
                Date accountExpDate = new Date(accountExpMillis);

                //결제검증을 마친 이후기 때문에 결제로그 저장
                DochaPaymentLogDto payLog = new DochaPaymentLogDto();
                payLog.setRmIdx(rmIdx);
                payLog.setApprovalNumber(applyNum);
                payLog.setPaymentAmount(Integer.toString(payment));
                payLog.setOrgMsg(orgMsg);
                payLog.setApprovalYn(applyNum == null ? "N" : "Y");
                payLog.setPaymentRequestAmount(Integer.toString(dailyStandardPay + insuranceFee));
                payLog.setAccountExpDt(accountExpDate);
                payLog.setPlIdx(plIdx);
                payLog.setPdIdx(pdIdx);
                dao.insertPaymentLog(payLog);

            }

            if (payServiceException != null) {
                String impKey = "4501580280430211";
                String impSecret = "QsXVWLFqiFyNtE1kJqIMIVXoJicFs0eisceoXUOgAn3315oKw7xe2vCMNPsfPGyhSNGPHv6hS9kUfJFL";
                String url = "https://api.iamport.kr";
                paramMap.set("cancel_request_amount", payment);
                paramMap.set("merchant_uid", merchantUid);
                paramMap.set("reason", "결제 중 실패");

                //결제검증 혹은 주문저장 실패이므로 결제취소처리 로직
                if (payment == 0) {
                    String accessToken = getAccessToken(impKey, impSecret, url);

                    //헤더에 AccessToken 설정
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Authorization", accessToken);

                    List<DochaScheduledDto> scheduledList = scheduledDao.selectScheduledListForCancel(paramMap);

                    for (int i = 0; i < scheduledList.size(); i++) {
                        paramMap.put("merchant_uid", scheduledList.get(i).getMerchantUid());
                        paramMap.put("customer_uid", scheduledList.get(i).getCustomerUid());

                        ObjectMapper mapper = new ObjectMapper();
                        mapper.readValue(connectImport(url + "/subscribe/payments/unschedule", headers, HttpMethod.POST, paramMap), Map.class);

                        scheduledDao.updateCancelScheduleStatus(paramMap);
                    }
//                    scheduledDao.updateCancelScheduleStatus(param);
//                    paymentDao.updateCancelScheduleReserve(param);
                }
                else
                    {
                        String accessToken = getAccessToken(impKey, impSecret, url);

                        //헤더에 AccessToken 설정
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("Authorization", accessToken);

                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, Object> resultMap = mapper.readValue(connectImport(url + "/payments/cancel", headers, HttpMethod.POST, paramMap), Map.class);

                        payData = (Map<String, Object>) result.get("response");
                }


            }
        }
    }

    /**
     * 아임포트 AccessKey 발급
     *
     * @param impKey    아임포트 key
     * @param impSecret 아임포트 시크릿 키
     * @param url       url
     * @return
     * @throws JsonMappingException
     * @throws JsonProcessingException
     * @throws Exception
     */
    private String getAccessToken(String impKey, String impSecret, String url) throws JsonMappingException, JsonProcessingException, Exception {
        Map<String, String> body = new LinkedHashMap<String, String>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        //파라미터로 imp_key, imp_secret 설정
        body.put("imp_key", impKey);
        body.put("imp_secret", impSecret);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(connectImport(url + "/users/getToken", headers, HttpMethod.POST, body), Map.class);
        Map<String, Object> dataMap = (Map<String, Object>) resultMap.get("response");

        return (String) dataMap.get("access_token");

    }

    /**
     * 아임포트 결제정보 조회
     *
     * @param impUid 아임포트 결제 유니크 key
     * @param token  아임포트 AccessToken
     * @param url    Url
     * @return
     * @throws JsonMappingException
     * @throws JsonProcessingException
     * @throws Exception
     */
    private Map<String, Object> getPaymentInfo(String impUid, String token, String url) throws JsonMappingException, JsonProcessingException, Exception {
        Map<String, String> body = new LinkedHashMap<String, String>();

        //헤더에 AccessToken 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(connectImport(url + "/payments/" + impUid, headers, HttpMethod.GET, null), Map.class);

        return resultMap;

    }

    /**
     * 아임포트 정기결제 저장
     *
     * @param uridx    아임포트 빌링키와 대응하는 유니크키(URIDX)
     * @param token    AccessToken
     * @param url      아임포트 URL
     * @param schedule 스케쥴 정보
     * @return
     * @throws JsonMappingException
     * @throws JsonProcessingException
     * @throws Exception
     */
    private Map<String, Object> postPaymentInfo(String uridx, String token, String url, HashMap<String, Object> schedule) throws JsonMappingException, JsonProcessingException, Exception {
        Map<String, String> body = new LinkedHashMap<String, String>();

        //헤더에 AccessToken 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> resultMap = mapper.readValue(connectImport(url + "/subscribe/payments/schedule", headers, HttpMethod.POST, schedule), Map.class);

        return resultMap;

    }

    private String connectImport(String url, HttpHeaders headers, HttpMethod method, Map body) throws Exception {

        RestTemplate connect = new RestTemplate();

        HttpEntity<Map> entity = new HttpEntity<Map>(body, headers);
        ResponseEntity<String> payResponse = null;
        try {
            payResponse = connect.exchange(url, method, entity, String.class);
        } catch (HttpServerErrorException ex) {

            logger.info("ImportConnect Error");
            logger.info("Error Request Url : " + url);
            logger.info("Error Request Body : " + body);
            logger.info("Error Response : " + ex.getResponseBodyAsString());
            logger.error("Error Request Url : " + url);
            logger.error("Error Request Body : " + body);
            logger.error(ex.getMessage());
            logger.error(ex.getResponseBodyAsString());
            logger.error("Error", ex);

            throw new Exception("Import Connection Error", ex);

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error("Error", e);

            throw new Exception("Import Connection Error");
        }

        String responseBody = payResponse.getBody();

        logger.info("Response Body : " + responseBody);

        return responseBody;
    }

    /**
     * 정기결제 스케쥴 시간 생성
     *
     * @param month 정기결제할 개월수
     * @return
     */
    private List<Long> getUnixTimeArray(int month, String rentStartDt, DochaMap paramMap) {

        ArrayList<Long> list = new ArrayList<Long>();

        //현재시간 생성
        LocalDateTime now = LocalDateTime.now();
        //첫번째 결제 시간은 현재시간보다 미래시간이어야 하므로, 현재시간 +1분을 더해 시간 생성
        LocalDateTime first = now.plusMinutes(2);
        //결제시간을 uinxtime으로 생성하여 리스트에 저장
        list.add(first.toEpochSecond(ZoneOffset.of("+9")));
        String startDt = null;
        LocalDateTime second = null;
        LocalDateTime tmp = null;

        // 연장결제가 아니면 대여 시점부터 두번 째 결제일을 정함
        if (paramMap.get("mode") == null) {
            // 두번 째 달 부터는 대여 시작 후 두번 째 달에 맞춰서 결제
            startDt = rentStartDt.substring(0, 4) + "-" + rentStartDt.substring(4, 6) + "-" + rentStartDt.substring(6, 8) + "T"
                    + rentStartDt.substring(8, 10) + ":" + rentStartDt.substring(10, 12) + ":00.000";
            second = LocalDateTime.parse(startDt).plusMonths(1);
        }


        //첫번쩨 결제시간을 저장했으므로, 결재개월수만큼 결제 스케쥴을 uinxtime으로 생성
        for (int i = 0; i < month - 1; i++) {
            tmp = second.plusMonths(Integer.toUnsignedLong(i));            // 1달 간격으로 납부

            list.add(tmp.toEpochSecond(ZoneOffset.of("+9")));
        }

        return list;

    }

    /**
     * 리뷰 등록
     * 
     * @return
     * @throws Exception 
     */
	@Override
	public int insertUserReview(HttpServletRequest request , DochaMap param) {
		// TODO Auto-generated method stub
		MultipartHttpServletRequest mRequest = (MultipartHttpServletRequest) request;
		String urIdx = param.getString("urIdx");
		String rmIdx = request.getParameter("rmIdx");
		int succCnt = 0;
		int failCnt = 0;
	
		DochaUserReviewDto userReview = DochaUserReviewDto.builder()
				.comment(request.getParameter("comment"))
				.rating(request.getParameter("rating"))
				.rmIdx(rmIdx)
				.urIdx(urIdx)
				.build();
		
		userReviewDao.insertUserReview(userReview);
		Integer rvIdx = userReview.getRvIdx();
		Iterator<String> fileIter = mRequest.getFileNames();
	    while (fileIter.hasNext()) {
			List<MultipartFile> multiPartFiles = mRequest.getFiles((String)fileIter.next());
			
			for (MultipartFile multipartFile : multiPartFiles) {	
		        
		        String fileRoot = properties.getTempFolderPath() + "userReview"+SEPARATION;
		        String originalFileName = multipartFile.getOriginalFilename();	//오리지날 파일명
		        
				if(originalFileName == null || originalFileName.equals("") ) 
					continue;
				
		        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));	//파일 확장자
				String savedFileName = UUID.randomUUID() + extension;	//저장될 파일 명
				
				
		        if (multipartFile.isEmpty())
		            throw new BadRequestException(IMAGE_IS_EMPTY, IMAGE_IS_EMPTY_MSG);

		        String uploadImageName = multipartFile.getOriginalFilename();
		        if (uploadImageName == null || uploadImageName.isEmpty())
		            throw new BadRequestException(IMAGE_PARSING_ERROR, IMAGE_PARSING_ERROR_MSG + "(이미지 파일이름이 없습니다.)");

		        String uploadImageMime = multipartFile.getContentType();
		        if (uploadImageMime == null || uploadImageMime.isEmpty() || !uploadImageMime.contains("image/"))
		            throw new BadRequestException(IMAGE_PARSING_ERROR, IMAGE_PARSING_ERROR_MSG + "(이미지 MIME 이 올바르지 않습니다.)");

		        int extensionIndexOf = uploadImageName.lastIndexOf('.');
		        if (extensionIndexOf == -1)
		            throw new BadRequestException(IMAGE_PARSING_ERROR, IMAGE_PARSING_ERROR_MSG + "(확장자가 존재하지 않습니다.)");

		        String uploadImageExtension = uploadImageName.substring(extensionIndexOf).replaceAll("\\.", "").toLowerCase();
		        if (!properties.getSupportImageExtension().contains(uploadImageExtension))
		            throw new BadRequestException(IMAGE_PARSING_ERROR, IMAGE_PARSING_ERROR_MSG + "(지원하지 않는 이미지 확장자 입니다.)");

		        long uploadImageSize = multipartFile.getSize();
		        if (uploadImageSize > properties.getUploadImageSize())
		            throw new BadRequestException(IMAGE_PARSING_ERROR, IMAGE_PARSING_ERROR_MSG + "(이미지 크기가 20MB를 초과 합니다.)");
				
				if (multipartFile.getSize() > 0) {

			        try {
			        	File file = new File(fileRoot + savedFileName); //서버일경우
			        	FileHelper.makeFolder(file.getParentFile());
			            file.createNewFile();
			            multipartFile.transferTo(file);
			            
						DochaUserReviewFileDto fileInfo = DochaUserReviewFileDto.builder()
								.rvIdx(rvIdx)
								.fileNm(savedFileName)
								.filePath("/img/userReview/")
								.orgFileNm(multipartFile.getOriginalFilename())
								.build();
						if(userReviewDao.insertUserReviewFile(fileInfo) > 0 ) {
							succCnt++;
						}else {
							failCnt++;
						}
						
			        } catch (Exception e) {
			        	logger.error("insertUserReview : 파일 생성중 오류 발생." , e.getCause());
			        }

			        if( multiPartFiles.size() != succCnt ) {
			        	logger.info("파일 등록 " + multiPartFiles.size() +"개중 " + succCnt + "성공 "  +  failCnt + "실패");
			        }
				}
			}
	    }
		return rvIdx;
	}

	@Override
	public int selectMyReviewCnt(DochaMap paramMap) {
		// TODO Auto-generated method stub
		return userReviewDao.selectMyReviewCnt(paramMap);
	}

}

