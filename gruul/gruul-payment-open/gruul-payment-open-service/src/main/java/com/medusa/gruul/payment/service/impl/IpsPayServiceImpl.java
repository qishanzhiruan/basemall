package com.medusa.gruul.payment.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.collect.Maps;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.payment.api.constant.ReturnCodeConstant;
import com.medusa.gruul.payment.api.entity.Payment;
import com.medusa.gruul.payment.api.entity.PaymentRecord;
import com.medusa.gruul.payment.api.entity.PaymentWechat;
import com.medusa.gruul.payment.api.enums.FeeTypeEnum;
import com.medusa.gruul.payment.api.enums.IpsPayCodeStatusEnum;
import com.medusa.gruul.payment.api.enums.WxPayEnum;
import com.medusa.gruul.payment.api.model.dto.IpsPayResultDto;
import com.medusa.gruul.payment.api.model.dto.PayRequestDto;
import com.medusa.gruul.payment.api.model.dto.PayResultDto;
import com.medusa.gruul.payment.api.model.dto.WxPayResultDto;
import com.medusa.gruul.payment.api.model.param.ips.*;
import com.medusa.gruul.payment.api.util.GlobalConstant;
import com.medusa.gruul.payment.api.util.GlobalTools;
import com.medusa.gruul.payment.conf.PayProperty;
import com.medusa.gruul.payment.handler.AbstractHandler;
import com.medusa.gruul.payment.handler.WxPayMiniHandler;
import com.medusa.gruul.payment.service.IPaymentRecordService;
import com.medusa.gruul.payment.service.IPaymentService;
import com.medusa.gruul.payment.service.IPaymentWechatService;
import com.medusa.gruul.payment.service.IpsPayService;
import com.medusa.gruul.platform.api.entity.MiniInfo;
import com.medusa.gruul.platform.api.feign.RemoteMiniInfoService;
import com.medusa.gruul.platform.api.model.dto.ShopConfigDto;
import com.medusa.gruul.platform.api.model.vo.PayInfoVo;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;


/**
 * @author create by zq
 * @date created in 2019/11/18
 */
@Service(value = "ipsPayServiceImpl")
@Log
public class IpsPayServiceImpl extends AbstractHandler implements IpsPayService {

    @Autowired
    private RemoteMiniInfoService remoteMiniInfoService;

    @Autowired
    private PayProperty payProperty;

    @Autowired
    private IPaymentService paymentService;

    @Autowired
    private IPaymentWechatService paymentWechatService;

    @Autowired
    private IPaymentRecordService paymentRecordService;

    @Autowired
    private WxPayMiniHandler wxPayMiniHandler;

    @Autowired
    private IPaymentService iPaymentService;


    /**
     * ?????? jsApi ?????????
     *
     * @param payRequestDto
     * @return PayResultDto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultDto handle(PayRequestDto payRequestDto) {
        PayResultDto test = this.test(payRequestDto);
        /** ?????????????????????????????????????????? */
        if (test != null) {
            return test;
        }

        if (StringUtils.isBlank(payRequestDto.getTenantId())) {
            throw new ServiceException("???????????????????????????");
        }
        ShopConfigDto shopConfig = remoteMiniInfoService.getShopConfig(payRequestDto.getTenantId());
        if (shopConfig == null) {
            throw new ServiceException("?????????????????????");
        }
        PayInfoVo payInfo = shopConfig.getPayInfo();
        if (payInfo == null) {
            throw new ServiceException("?????????????????????");
        }
        MiniInfo miniInfo = shopConfig.getMiniInfo();
        if (miniInfo == null) {
            throw new ServiceException("??????????????????");
        }
        log.info("miniInfo log = " + miniInfo.toString());
        payRequestDto.setFeeType(FeeTypeEnum.CNY.getName());
        /** ?????????????????? */
        Payment payment = this.generatePayment(payRequestDto, payProperty.getIpsWorkerId(), payProperty.getIpsDatacenterId());
        paymentService.save(payment);
        /** ???????????????????????? */
        innerHandleChannelData(payment, payRequestDto);
        /** ?????????????????? */
        PaymentRecord paymentRecord = this.generateRecod(payRequestDto);
        paymentRecord.setPaymentId(payment.getId());

        // ??????IpsWxPlatformPay
        IpsWxPlatformPay ipsWxPlatformPay = innerHandleIpsWxPlatformPay(payRequestDto, payment, payInfo);
        String data = JSONObject.toJSONString(ipsWxPlatformPay);
        log.info(String.format("IPS ?????????data: %s", data));
        String signData = GlobalTools.encryptAes(payInfo.getIpsAes(), data);
        log.info(String.format("IPS ?????????signData: %s", signData));

        IpsRequestPay ipsRequestPay = innerHandleIpsRequestPay(signData, payInfo);
        log.info(String.format("IPS innerHandleIpsRequestPay Result : %s", ipsRequestPay.toString()));
        String object = HttpUtil.toParams(GlobalTools.getObjectMap(ipsRequestPay));

        log.info(String.format("IPS ?????????????????????: %s", object));
        String signObject = GlobalTools.signSha256withRsa(payInfo.getIpsRsaPrivateKey(), payInfo.getIpsCertificatePsw(), object);
        ipsRequestPay.setSign(signObject);
        log.info(String.format("IPS ?????????signObject: %s", signObject));

        Map map = GlobalTools.getObjectMap(ipsRequestPay);
        paymentRecord.setSendParam(String.format("IPS ????????????????????????????????? : %s, ipsRequestPay ??????????????? : %s",
                JSON.toJSONString(ipsWxPlatformPay), JSON.toJSONString(ipsRequestPay)));
        paymentRecordService.save(paymentRecord);

        String result = null;
        IpsPayResultDto.PayDataDto payData;
        IpsPayResultDto.PayDataDto.PayInfo payInfoData;
        try {
            result = HttpUtil.post(payProperty.getIpsUrl() + payProperty.getIpsTradePlatformPaySubUrl(), map);
            log.info("result : " + result);
            // ??????????????????
            payData = innerHandlePayData(result, payInfo);
            String payInfoString = payData.getPayInfo();
            if (StringUtils.isBlank(payInfoString)) {
                throw new ServiceException(String.format("??????ips??????payInfo????????????! payData: %s", payData));
            }

            payInfoData = JSONObject.parseObject(payInfoString, IpsPayResultDto.PayDataDto.PayInfo.class);
            if (null == payInfoData) {
                throw new ServiceException(String.format("??????ips??????payInfoData????????????! payInfo: %s", payInfoString));
            }

            JSONObject jsonObject = JSONObject.parseObject(payInfoString);
            payInfoData.setPackageStr(jsonObject.getString(GlobalConstant.STRING_PACKAGE));
        } catch (Exception e) {
            paymentRecord.setNotifyParam(JSONObject.toJSONString(e.getMessage()));
            throw new ServiceException(String.format("??????ips?????????????????????, ????????????: %s", e));
        } finally {
            paymentRecord.setNotifyParam(result);
            paymentRecordService.updateById(paymentRecord);
        }
        return innerHandleResponseData(payData, payInfoData, payment);
    }


    /**
     * ips?????????????????????
     *
     * @param request res
     * @return str
     */
    @Override
    public String ipsNotify(HttpServletRequest request) {
        String data = request.getParameter("data");
        String version = request.getParameter("version");
        String merCode = request.getParameter("merCode");
        String ts = request.getParameter("ts");
        String nonceStr = request.getParameter("nonceStr");
        String format = request.getParameter("format");
        String encryptType = request.getParameter("encryptType");
        String signType = request.getParameter("signType");
        String notifyType = request.getParameter("notifyType");
        String sign = request.getParameter("sign");
        String param = JSONObject.toJSONString(new IpsCallBackResponse(version, ts, merCode, nonceStr,
                format, encryptType, data, signType, notifyType));
        log.info(String.format("ipsNotify ????????????: %s, sign %s", param, sign));
        String verifyObject;
        String decryptData;
        boolean verifyResult;
        try {
            if (StringUtils.isNotBlank(sign)) {
                ShopConfigDto shopConfig = remoteMiniInfoService.getShopConfig(merCode);
                if (shopConfig == null) {
                    throw new ServiceException("????????????shopConfig");
                }
                PayInfoVo payInfoVo = shopConfig.getPayInfo();
                if (null == payInfoVo || StringUtils.isBlank(payInfoVo.getIpsSha())) {
                    throw new ServiceException(String.format("????????????ips ??????????????????, merCode: %s, ????????????: %s", merCode, String.format("ipsNotify ????????????: %s", param)));
                }

                IpsCallBackResponse backResponse = new IpsCallBackResponse(version, ts, merCode, nonceStr,
                        format, encryptType, data, signType, notifyType);
                verifyObject = HttpUtil.toParams(GlobalTools.getObjectMap(backResponse));
                log.info(String.format("?????????????????? ??????: %s", verifyObject));
                verifyResult = GlobalTools.verifySha256withRsa(payInfoVo.getIpsSha(), verifyObject, sign);
                log.info(String.format("??????????????????end ??????: %s", verifyObject));
                if (verifyResult) {
                    decryptData = GlobalTools.decryptAes(payInfoVo.getIpsAes(), data);
                    log.info(String.format("????????????: %s ", JSONObject.toJSONString(decryptData)));
                    IpsCallBackSynResponse response = JSONObject.parseObject(decryptData, IpsCallBackSynResponse.class);

                    Payment payment = iPaymentService.getBaseMapper().selectOne(new QueryWrapper<Payment>().eq("transaction_id", response.getTrxId()));

                    // ??????????????????
                    innerHandleNotifyResponse(payment, decryptData);

                    if (GlobalConstant.STRING_SUCCESS.equals(response.getTrxStatus())
                            && null != response.getTrxAmt()
                            && response.getTrxAmt().equals(response.getPayAmt())) {
                        log.info(JSONObject.toJSONString(payment));

                        // ??????????????????
                        return paymentService.innerHandleCallback(payment, decryptData);
                    }
                }
            }
        } catch (Throwable ex) {
            log.warning("ipsNotify ???????????? ????????????: " + ex.getMessage());
            return ReturnCodeConstant.FAIL;
        }
        return ReturnCodeConstant.FAIL;
    }


    /**
     * ??????????????????
     *
     * @param payment
     * @param decryptData
     */
    private void innerHandleNotifyResponse(Payment payment, String decryptData) {
        PaymentRecord record = paymentRecordService.getBaseMapper().selectOne(new QueryWrapper<PaymentRecord>().eq("payment_id", payment.getId()));
        record.setNotifyParam(decryptData);
        paymentRecordService.getBaseMapper().updateById(record);
    }


    /**
     * ??????????????????
     *
     * @param result
     * @param payInfo
     * @return
     */
    private IpsPayResultDto.PayDataDto innerHandlePayData(String result, PayInfoVo payInfo) {

        if (StringUtils.isBlank(result)) {
            throw new ServiceException(String.format("????????????ips????????????, ????????????: %s", result));
        }

        IpsResponsePay responsePay = JSONObject.parseObject(result, IpsResponsePay.class);
        if (!IpsPayCodeStatusEnum.SUCCESS.getCode().equals(responsePay.getRespCode())) {
            throw new ServiceException(String.format("ips???????????? ?????????????????????????????????! ????????????: %s", responsePay.toString()));
        }

        if (StringUtils.isBlank(responsePay.getSign())) {
            throw new ServiceException(String.format("ips ???????????? ??????sign??????! ????????????: %s", responsePay.toString()));
        }

        Map<String, Object> returnMap = Maps.newHashMap();
        returnMap.put(GlobalConstant.STRING_DATA, responsePay.getData());
        returnMap.put(GlobalConstant.STRING_RESP_CODE, responsePay.getRespCode());
        returnMap.put(GlobalConstant.STRING_RESP_MSG, responsePay.getRespMsg());
        String returnSignData = HttpUtil.toParams(returnMap);

        if (!GlobalTools.verifySha256withRsa(payInfo.getIpsSha(), returnSignData, responsePay.getSign())) {
            throw new ServiceException(String.format("ips???????????????????????????! data: %s, sign: %s", returnSignData, responsePay.getSign()));
        }

        String encryptionData = responsePay.getData();
        if (StringUtils.isBlank(encryptionData)) {
            throw new ServiceException(String.format("??????ips??????data????????????! jsonObject: %s", responsePay.toString()));
        }


        String responseData = GlobalTools.decryptAes(payInfo.getIpsAes(), encryptionData);
        if (StringUtils.isBlank(responseData)) {
            throw new ServiceException(String.format("??????ips??????data ????????????! IpsAesKey: %s, encryptionData: %s", payInfo.getIpsAes(), encryptionData));
        }

        return JSONObject.parseObject(responseData, IpsPayResultDto.PayDataDto.class);
    }


    /**
     * ????????????????????????
     *
     * @param payment
     * @param payRequestDto
     */
    private void innerHandleChannelData(Payment payment, PayRequestDto payRequestDto) {
        PaymentWechat paymentWechat = new PaymentWechat();
        paymentWechat.setPaymentId(payment.getId());
        paymentWechat.setTradeType(WxPayEnum.IPS_JS_API.getType());
        paymentWechat.setSubject(payRequestDto.getSubject());
        paymentWechat.setOutTradeNo(payRequestDto.getOutTradeNo());
        paymentWechat.setTenantId(payRequestDto.getTenantId());
        paymentWechat.setOpenId(payRequestDto.getOpenId());
        paymentWechatService.save(paymentWechat);
    }


    /**
     * ????????????????????????
     *
     * @param payInfoData
     * @param payData
     * @param payment
     * @return
     */
    private PayResultDto innerHandleResponseData(IpsPayResultDto.PayDataDto payData, IpsPayResultDto.PayDataDto.PayInfo payInfoData, Payment payment) {
        PayResultDto test = new PayResultDto();
        WxPayResultDto wxPayResultDto = new WxPayResultDto();
        wxPayResultDto.setAppId(payInfoData.getAppId());
        wxPayResultDto.setNonceStr(payInfoData.getNonceStr());
        wxPayResultDto.setPackageValue(payInfoData.getPackageStr());
        wxPayResultDto.setPaySign(payInfoData.getPaySign());
        wxPayResultDto.setSignType(payInfoData.getSignType());
        wxPayResultDto.setTimeStamp(payInfoData.getTimeStamp());
        wxPayResultDto.setResultCode(GlobalConstant.STRING_SUCCESS);
        wxPayResultDto.setTradeType(payData.getSceneType());
        wxPayResultDto.setTransactionId(payment.getTransactionId().toString());
        wxPayMiniHandler.transactionCloseTime(payment);
        test.setWxResult(wxPayResultDto);
        log.info(JSONObject.toJSONString(test));
        return test;
    }


    /**
     * ??????IpsRequestPay
     *
     * @param signData
     * @param payInfo
     * @return
     */
    private IpsRequestPay innerHandleIpsRequestPay(String signData, PayInfoVo payInfo) {
        IpsRequestPay accEntryRequest = new IpsRequestPay();
        accEntryRequest.setData(signData);
        accEntryRequest.setVersion(payProperty.getIpsVersion());
        accEntryRequest.setMerCode(payInfo.getIpsMerCode());
        accEntryRequest.setEncryptType(GlobalConstant.STRING_AES);
        accEntryRequest.setFormat(GlobalConstant.STRING_JSON);
        accEntryRequest.setNonceStr(
                UUID.randomUUID().toString().replace(
                        GlobalConstant.STRING_BAR, GlobalConstant.STRING_EMPTY));
        accEntryRequest.setTs(new SimpleDateFormat(GlobalConstant.STRING_YYYYMMDDHHMMSS).format(new Date()));
        accEntryRequest.setSignType(GlobalConstant.STRING_RSA_2);
        return accEntryRequest;
    }


    /**
     * ?????? IpsWxPlatformPay
     *
     * @param payRequestDto
     * @param payment
     * @param payInfo
     * @return
     */
    private IpsWxPlatformPay innerHandleIpsWxPlatformPay(PayRequestDto payRequestDto, Payment payment, PayInfoVo payInfo) {
        ShopConfigDto shopConfig = remoteMiniInfoService.getShopConfig(payRequestDto.getTenantId());
        if (shopConfig == null) {
            throw new ServiceException("?????????????????????");
        }
        MiniInfo miniInfo = shopConfig.getMiniInfo();
        if (miniInfo == null) {
            throw new ServiceException("??????????????????");
        }

        if (null == miniInfo || StringUtils.isBlank(miniInfo.getAppId())) {
            throw new ServiceException("???????????????");
        }
        IpsWxPlatformPay platformPay = new IpsWxPlatformPay();
        platformPay.setMerType(GlobalConstant.STRING_ZERO);

        /** ?????? 1001 */
        platformPay.setPlatCode("1001");
        platformPay.setAppId(miniInfo.getAppId());
        platformPay.setAccount(payInfo.getIpsAccCode());
        platformPay.setSceneType(WxPayEnum.IPS_JS_API.getCode());
        platformPay.setTrxId(payment.getTransactionId().toString());
        platformPay.setProductType("9517");
        platformPay.setTrxDtTm(new SimpleDateFormat(GlobalConstant.STRING_YYYYMMDD).format(new Date()));
        platformPay.setTrxCcyCd(FeeTypeEnum.CNY.getIpsCode());
        platformPay.setTrxAmt(payRequestDto.getTotalFee().toString());
        platformPay.setOpenId(payRequestDto.getOpenId());
        platformPay.setNotifyUrl(payProperty.getIpsNotify());
        platformPay.setAttach(payRequestDto.getBusinessParams());
        platformPay.setExpireDtTm(innerHandleExTime(payRequestDto.getTimeoutExpress()));
        platformPay.setGoodsDesc(payRequestDto.getBody());
        IpsWxPlatformPay.ExtFields extFields = platformPay.new ExtFields();
        extFields.setClientIp(payRequestDto.getTerminalIp());
        platformPay.setExtFields(extFields);
        return platformPay;
    }


    /**
     * m-?????????h-?????????d-?????? 1m 24h 1d
     *
     * @param timeoutExpress ????????????
     * @return
     */
    private String innerHandleExTime(String timeoutExpress) {
        DateTime endTime;
        if (StrUtil.isNotEmpty(timeoutExpress)) {
            int index = timeoutExpress.length() - 1;
            //????????????
            Integer time = Integer.valueOf(StrUtil.subWithLength(timeoutExpress, 0, index));
            //????????????
            String unit = StrUtil.subWithLength(timeoutExpress, index, 1);
            //m-?????????h-?????????d-??????
            switch (unit) {
                case "m":
                    endTime = DateUtil.offsetMinute(DateUtil.date(), time);
                    break;
                case "h":
                    endTime = DateUtil.offsetHour(DateUtil.date(), time);
                    break;
                case "d":
                    endTime = DateUtil.offsetDay(DateUtil.date(), time);
                    break;
                default:
                    endTime = DateUtil.offsetMinute(DateUtil.date(), 15);
                    break;
            }
        } else {
            endTime = DateUtil.offsetMinute(DateUtil.date(), 15);
        }
        return new SimpleDateFormat(GlobalConstant.STRING_YYYYMMDDHHMMSS).format(endTime);
    }

}
