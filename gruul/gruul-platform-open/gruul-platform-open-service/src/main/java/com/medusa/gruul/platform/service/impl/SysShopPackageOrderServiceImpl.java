package com.medusa.gruul.platform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.medusa.gruul.common.core.constant.CommonConstants;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.core.util.CurUserUtil;
import com.medusa.gruul.common.core.util.LocalDateTimeUtils;
import com.medusa.gruul.common.core.util.PageUtils;
import com.medusa.gruul.common.data.tenant.TenantContextHolder;
import com.medusa.gruul.platform.api.entity.*;
import com.medusa.gruul.platform.conf.MeConstant;
import com.medusa.gruul.platform.constant.AgentNoticeEnum;
import com.medusa.gruul.platform.mapper.SysShopPackageOrderMapper;
import com.medusa.gruul.platform.model.dto.OrderOptionDto;
import com.medusa.gruul.platform.model.dto.PayDto;
import com.medusa.gruul.platform.model.dto.ShopPackageOrderDto;
import com.medusa.gruul.platform.model.dto.SysShopPackageOrderDto;
import com.medusa.gruul.platform.model.vo.*;
import com.medusa.gruul.platform.service.*;
import com.medusa.gruul.platform.stp.StpAgentUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * ????????????????????? ???????????????
 * </p>
 *
 * @author whh
 * @since 2020-08-01
 */
@Service
@Slf4j
public class SysShopPackageOrderServiceImpl extends ServiceImpl<SysShopPackageOrderMapper, SysShopPackageOrder> implements ISysShopPackageOrderService {

    @Autowired
    private ISysShopPackageService sysShopPackageService;
    @Autowired
    private IPlatformShopInfoService platformShopInfoService;
    @Autowired
    private ISystemConfService systemConfService;
    @Autowired
    private ISysShopInvoiceOrderService sysShopInvoiceOrderService;
    @Autowired
    private IAccountInfoService accountInfoService;
    @Autowired
    private IPlatformShopTemplateInfoService templateInfoService;
    @Autowired
    private IPlatformAccountBalanceRecordService platformAccountBalanceRecordService;
    @Autowired
    private IMiniInfoService miniInfoService;
    @Autowired
    private IPlatformShopTemplateInfoService platformShopTemplateInfoService;


    /**
     * ????????????????????????
     */
    private static final Map<Integer, String> PAY_NOTIFY = new HashMap<Integer, String>() {
        private static final long serialVersionUID = -8177029131574364968L;

        {
            put(2, "wx");
            put(3, "alipay");
        }
    };

    /**
     * ????????????
     *
     * @param shopPackageOrderDto com.medusa.gruul.platform.model.dto.ShopPackageOrderDto
     * @return
     */
    private SysShopPackageOrder generateOrder(ShopPackageOrderDto shopPackageOrderDto) {
        SysShopPackageOrder order = null;
        //??????????????????????????????
        SysShopPackage buyShopPackage = sysShopPackageService.getById(shopPackageOrderDto.getPackageId());
        if (buyShopPackage == null) {
            throw new ServiceException("???????????????");
        }
        //????????????????????????
        PlatformShopInfo platformShopInfo = platformShopInfoService.getById(shopPackageOrderDto.getShopId());

        //1-?????? 2-??????  3-??????
        switch (shopPackageOrderDto.getOptionType()) {
            case 1:
                order = order(buyShopPackage, platformShopInfo, shopPackageOrderDto);
                break;
            case 2:
                order = pacakgeRenew(buyShopPackage, platformShopInfo, shopPackageOrderDto);
                break;
            case 3:
                order = upgrade(buyShopPackage, platformShopInfo, shopPackageOrderDto);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + shopPackageOrderDto.getOptionType());
        }
        if (ObjectUtil.isNull(order)) {
            throw new ServiceException("??????????????????");
        }
        return order;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminBuy(ShopPackageOrderDto shopPackageOrderDto) {
        //????????????
        SysShopPackageOrder order = generateOrder(shopPackageOrderDto);
        //????????????
        packagePayNotify(order.getOrderNum());
    }

    @Override
    public CalculateOrderPriceVo calculateOrderPrice(ShopPackageOrderDto shopPackageOrderDto) {
        if (shopPackageOrderDto.getPackageId() == null) {
            throw new ServiceException("????????????");
        }
        Boolean flag = (shopPackageOrderDto.getOptionType() < CommonConstants.NUMBER_ONE &&
                shopPackageOrderDto.getOptionType() > CommonConstants.NUMBER_THREE);
        if (shopPackageOrderDto.getOptionType() == null || flag) {
            throw new ServiceException("????????????");
        }
        if (shopPackageOrderDto.getBuyPeriod() == null || shopPackageOrderDto.getBuyPeriod() < 0) {
            throw new ServiceException("?????????????????????");
        }

        SysShopPackage buyShopPackage = sysShopPackageService.getById(shopPackageOrderDto.getPackageId());
        if (buyShopPackage == null) {
            throw new ServiceException("???????????????");
        }
        CalculateOrderPriceVo vo = new CalculateOrderPriceVo();
        switch (shopPackageOrderDto.getOptionType()) {
            case 1:
            case 2:
                vo.setPreferentialPrice(calculatePreferentialPrice(buyShopPackage, shopPackageOrderDto.getBuyPeriod()));
                vo.setActualPrice(calculateActualPrice(buyShopPackage, shopPackageOrderDto.getBuyPeriod()));
                break;
            case 3:
                Long shopId = shopPackageOrderDto.getShopId();
                if (shopId == null) {
                    throw new ServiceException("?????????????????????");
                }
                PlatformShopInfo shopInfo = platformShopInfoService.getById(shopId);
                if (shopInfo == null) {
                    throw new ServiceException("???????????????");
                }
                if (shopInfo.getPackageOrderId() == null) {
                    throw new ServiceException("?????????????????????????????????");
                }
                //??????????????????????????????
                SysShopPackageOrder oldPackageOrder = this.getById(shopInfo.getPackageOrderId());
                //???????????????: ???????????????????????? * (????????????????????? / 365)
                //??????????????????????????????
                Long betweenTwoTime = LocalDateTimeUtils.betweenTwoTime(LocalDateTime.now(), oldPackageOrder.getPackageEndTime(), ChronoUnit.DAYS);
                BigDecimal dayPrice = null;
                //??????????????????????????????
                switch (buyShopPackage.getPackagePriceUnit()) {
                    case 1:
                        dayPrice = buyShopPackage.getPackagePrice();
                        break;
                    case 2:
                        dayPrice = NumberUtil.div(buyShopPackage.getPackagePrice(), 30);
                        break;
                    case 3:
                        dayPrice = NumberUtil.div(buyShopPackage.getPackagePrice(), 365);
                        break;
                    default:
                        throw new ServiceException("??????????????????");
                }
                BigDecimal price = NumberUtil.mul(dayPrice, betweenTwoTime);
                vo.setActualPrice(NumberUtil.round(price, 2));
                break;
            default:
                throw new ServiceException("????????????");
        }
        if (vo.getPreferentialPrice() == null) {
            vo.setPreferentialPrice(NumberUtil.round(vo.getActualPrice(), 2));
        }
        //Todo ???????????????????????????????????????????????????
        return vo;
    }

    @Override
    public void adminCreateShopBuy(Integer orderSource, PlatformShopInfo info, SysShopPackage sysShopPackage, BigDecimal orderPirce, Integer givePackageTime) {
        SysShopPackageOrder packageEntify = createPackageEntify(info, sysShopPackage);
        //????????????????????????
        packageEntify.setPackageTime(givePackageTime);
        packageEntify.setPayType(CommonConstants.NUMBER_FIVE);
        packageEntify.setOrderSource(orderSource);
        packageEntify.setIsAgreed(CommonConstants.NUMBER_ONE);
        packageEntify.setIsAutomaticDeduction(CommonConstants.NUMBER_ZERO);
        packageEntify.setOrderType(CommonConstants.NUMBER_ONE);
        packageEntify.setAmountPayable(orderPirce);
        packageEntify.setPaidPayable(orderPirce);
        packageEntify.setPackageStartTime(info.getCreateTime());
        LocalDateTime packageEndTime = LocalDateTimeUtil.offset(packageEntify.getPackageStartTime(), givePackageTime, ChronoUnit.DAYS);
        packageEntify.setPackageEndTime(packageEndTime);
        this.save(packageEntify);
        packagePayNotify(packageEntify.getOrderNum());
    }

    @Override
    public String notifyAlipay(Map<String, String> toMap) {
        if (MapUtil.isEmpty(toMap)) {
            log.warn("????????????");
            return "fail";
        }
        String outTradeNo = toMap.get("out_trade_no");
        if (packagePayNotify(outTradeNo)) {
            return "success";
        }
        return "fail";
    }

    @Override
    public Integer selectBoughtEnterpriseVersion(String tenantId) {
        return this.baseMapper.selectBoughtEnterpriseVersion(tenantId);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public BuyPackageOrderVo optionPackage(ShopPackageOrderDto shopPackageOrderDto) {
        //????????????
        SysShopPackageOrder order = generateOrder(shopPackageOrderDto);

        //??????????????????,????????????
        SystemConfigVo systemConfigVo = systemConfService.getTypeInfo(CommonConstants.NUMBER_ZERO);
        if (ObjectUtil.isEmpty(systemConfigVo)) {
            throw new ServiceException("??????????????????");
        }

        try {
            BuyPackageOrderVo vo = new BuyPackageOrderVo();
            vo.setOrderId(order.getId());
            String url = "/api/platform/sys-shop-package-order/notify/";
//            if (StpAgentUtil.isLogin()) {
//                url = "/api/platform/agent/console/package/notify/";
//            }
            //??????????????????
            String notifyUrl = systemConfigVo.getSystemConfig().getMiniDomain()
                    .concat(url);
            //1:????????????2:??????3:?????????4:????????????
            switch (order.getPayType()) {
                case 1:
                    //????????????????????????????????????
                    accountBlanceBuy(order);
                    break;
                case 2:
                    notifyUrl = notifyUrl.concat(PAY_NOTIFY.get(shopPackageOrderDto.getPayType()));
                    PayDto payDto = systemConfService.wxQrcodePay(notifyUrl,
                            order.getOrderNum(), order.getPaidPayable().toString(), "????????????", "package");
                    vo.setCodeUrl(payDto.getCodeUrl());
                    break;
                case 3:
                    notifyUrl = notifyUrl.concat(PAY_NOTIFY.get(shopPackageOrderDto.getPayType()));
                    PayDto payDto1 = systemConfService.aliPayQrcodePay(notifyUrl, "????????????", order.getPaidPayable().toString(), order.getOrderNum());
                    vo.setCodeUrl(payDto1.getCodeUrl());
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + order.getPayType());
            }

            return vo;
        } catch (WxPayException e) {
            e.printStackTrace();
            throw new ServiceException("??????????????????");
        }


    }


    /**
     * ????????????????????????(??????????????????)
     *
     * @param order com.medusa.gruul.platform.api.entity.SysShopPackageOrder
     */
    private void accountBlanceBuy(SysShopPackageOrder order) {

        AccountInfo accountInfo = accountInfoService.getById(order.getAccountId());
        if (accountInfo == null) {
            throw new ServiceException("???????????????");
        }
        BigDecimal newBalance = NumberUtil.sub(accountInfo.getBalance(), order.getPaidPayable());
        BigDecimal zero = new BigDecimal("0.0");
        if (newBalance.compareTo(zero) < 0) {
            throw new ServiceException("??????????????????");
        }
        AccountInfo up = new AccountInfo();
        up.setId(accountInfo.getId());
        up.setBalance(newBalance);
        accountInfoService.updateById(up);
        Integer consumptionType = CommonConstants.NUMBER_ONE;
        if (order.getOrderType().equals(CommonConstants.NUMBER_THREE) ||
                order.getOrderType().equals(CommonConstants.NUMBER_TWO)) {
            consumptionType = CommonConstants.NUMBER_TWO;
        }
        //????????????????????????
        platformAccountBalanceRecordService.newBlanceDeail(
                accountInfo, consumptionType, order.getOrderNum(), order.getPaidPayable());
        //??????????????????
        packagePayNotify(order.getOrderNum());
    }


    @Override
    public String notifyWx(String xmlResult) {
        WxPayOrderNotifyResult payOrderNotifyResult = WxPayOrderNotifyResult.fromXML(xmlResult);
        String outTradeNo = payOrderNotifyResult.getOutTradeNo();
        if (packagePayNotify(outTradeNo)) {
            return WxPayNotifyResponse.success("????????????");
        } else {
            return WxPayNotifyResponse.fail("????????????");
        }
    }

    /**
     * @param outTradeNo ??????id
     * @return true ??????  false ??????
     * @throws ServiceException
     */
    public boolean packagePayNotify(String outTradeNo) throws ServiceException {
        try {
            SysShopPackageOrder order = this.getByOrderNum(outTradeNo);
            if (order == null) {
                log.warn("orderNum :{} ????????????", outTradeNo);
                return Boolean.FALSE;
            }
            //??????????????????
            if (order.getStatus().equals(CommonConstants.NUMBER_TWO)) {
                return Boolean.TRUE;
            }
            PlatformShopInfo shopInfo = platformShopInfoService.getByTenantId(order.getTenantId());
            if (shopInfo == null) {
                log.error("????????????????????? TenantId: {}", order.getTenantId());
                return Boolean.FALSE;
            }
            //??????????????????
            SysShopPackageOrder up = new SysShopPackageOrder();
            up.setId(order.getId());
            up.setStatus(CommonConstants.NUMBER_TWO);
            //????????????,?????????????????????
            order.setStatus(CommonConstants.NUMBER_TWO);
            //?????????????????????
            if (order.getPayType().equals(CommonConstants.NUMBER_FOUR)) {
                up.setAuditorStatus(CommonConstants.NUMBER_ONE);
                up.setIsReceived(CommonConstants.NUMBER_ONE);
            }
            if (!this.updateById(up)) {
                log.error("??????????????????????????????");
                return Boolean.FALSE;
            }
            PlatformShopInfo upShopInfo = new PlatformShopInfo();
            upShopInfo.setId(shopInfo.getId());
            upShopInfo.setPackageId(order.getPackageId());
            upShopInfo.setPackageOrderId(order.getId());
            upShopInfo.setDueTime(order.getPackageEndTime());
            upShopInfo.setIsDue(CommonConstants.NUMBER_ZERO);
            platformShopInfoService.updateById(upShopInfo);
            AccountInfo accountInfo = accountInfoService.getById(order.getAccountId());
            //Todo ??????????????????,????????????????????????????????????,?????????????????????????????????????????????
            return Boolean.TRUE;
        } catch (Exception e) {
            e.printStackTrace();
            log.error("??????????????????====> ????????? :{}", outTradeNo);
            throw new ServiceException("????????????????????????");
        }
    }

    /**
     * ????????????
     *
     * @param order      ??????????????????
     * @param shopInfo   ????????????
     * @param upShopInfo ?????????????????????
     */
    private void agentNotice(SysShopPackageOrder order, PlatformShopInfo shopInfo, PlatformShopInfo upShopInfo, Long agentId) {
        //??????id??????
        if (agentId == null || agentId == 0) {
            return;
        }
        //???????????????????????????,????????????????????????????????????
        LinkedList<String> titles = new LinkedList<>();
        MiniInfo miniInfo = miniInfoService.getById(shopInfo.getTenantId());
        String miniName = "-";
        if (miniInfo != null) {
            miniName = miniInfo.getMiniName();
        }
        titles.add(miniName);
        titles.add(shopInfo.getShopName());
        PlatformShopTemplateInfo shopTemplateInfo = platformShopTemplateInfoService.getById(shopInfo.getShopTemplateId());
        String key3 = "";
        AgentNoticeEnum noticeEnum = null;
        //1-??????  2-??????  3-??????
        switch (order.getOrderType()) {
            case 1:
                noticeEnum = AgentNoticeEnum.MSG_003;
                key3 = order.getPackageName();
                break;
            case 2:
                switch (order.getPackagePriceUnit()) {
                    case 1:
                        key3 = key3.concat(order.getPackageTime().toString()).concat("???");
                        break;
                    case 2:
                        key3 = key3.concat(((Double) NumberUtil.div(order.getPackageTime().doubleValue(), 30)).toString()).concat("???");
                        break;
                    case 3:
                        key3 = key3.concat(((Double) NumberUtil.div(order.getPackageTime().doubleValue(), 365)).toString()).concat("???");
                        break;
                    default:
                        break;
                }
                noticeEnum = AgentNoticeEnum.MSG_001;
                break;
            case 3:
                key3 = order.getPackageName();
                noticeEnum = AgentNoticeEnum.MSG_002;
                break;
            default:
                break;
        }
        String key4 = "";
        Long day = DateUtil.betweenDay(new Date(), LocalDateTimeUtils.convertLDTToDate(upShopInfo.getDueTime()), false);

        double surplus = NumberUtil.div(day.doubleValue(), 365, 0, RoundingMode.DOWN);
        if (surplus > 0) {
            key4 = surplus + "???";
            double daySurplus = (day - (surplus * 365));
            if (day > 0) {
                key4 = daySurplus + "???";
            }
        } else {
            key4 = day + "???";
        }
        //????????????,??????,????????????
        Map<String, String> content = Maps.of("key1", miniName, "key2", shopTemplateInfo.getName(), "key3", key3, "key4", key4);
        //Todo ??????????????????
    }




    @Override
    public SysShopPackageOrder getByOrderNum(String orderNum) {
        return this.getOne(new QueryWrapper<SysShopPackageOrder>().eq("order_num", orderNum));
    }

    @Override
    public List<BigDecimal> selectByAgentOrder(Long agentId) {
        return this.baseMapper.selectByAgentOrder(agentId);
    }


    /**
     * ????????????
     *
     * @param buyShopPackage ?????????
     * @param shopInfo       ????????????
     * @param orderDto       ????????????
     * @return com.medusa.gruul.platform.api.entity.SysShopPackageOrder
     */
    private SysShopPackageOrder upgrade(SysShopPackage buyShopPackage, PlatformShopInfo shopInfo, ShopPackageOrderDto orderDto) {
        Long packageOrderId = shopInfo.getPackageOrderId();
        if (packageOrderId == null) {
            throw new ServiceException("?????????????????????????????????,????????????");
        }
        //??????????????????????????????
        SysShopPackageOrder oldPackageOrder = this.getById(shopInfo.getPackageOrderId());
        if (System.currentTimeMillis() >=
                LocalDateTimeUtils.getMilliByTime(oldPackageOrder.getPackageEndTime())) {
            throw new ServiceException("???????????????????????????,??????????????????");
        }

        //????????????,??????????????????????????????
        SysShopPackage oldPackage = JSONObject.parseObject(oldPackageOrder.getPackageData(), SysShopPackage.class);
        if (oldPackage == null) {
            throw new ServiceException("??????????????????");
        }
        if (oldPackage.getLevel() > buyShopPackage.getLevel()) {
            throw new ServiceException("?????????????????????????????????????????????");
        }
        //???????????????: ???????????????????????? * (????????????????????? / 365)
        //??????????????????????????????
        Long betweenTwoTime = LocalDateTimeUtils.betweenTwoTime(LocalDateTime.now(), oldPackageOrder.getPackageEndTime(), ChronoUnit.DAYS);
        BigDecimal dayPrice = null;
        //??????????????????????????????
        switch (buyShopPackage.getPackagePriceUnit()) {
            case 1:
                dayPrice = buyShopPackage.getPackagePrice();
                break;
            case 2:
                dayPrice = NumberUtil.div(buyShopPackage.getPackagePrice(), 30);
                break;
            case 3:
                dayPrice = NumberUtil.div(buyShopPackage.getPackagePrice(), 365);
                break;
            default:
                throw new ServiceException("??????????????????");
        }
        BigDecimal price = NumberUtil.mul(dayPrice, betweenTwoTime);
        //Todo ???????????????????????????????????????????????????
        SysShopPackageOrder sysShopPackageOrder = createOrderEntify(shopInfo, buyShopPackage, orderDto);
        sysShopPackageOrder.setPackageTime(betweenTwoTime.intValue());
        sysShopPackageOrder.setOrderType(CommonConstants.NUMBER_THREE);
        sysShopPackageOrder.setAmountPayable(price);
        sysShopPackageOrder.setPaidPayable(price);
        //????????????????????????
        sysShopPackageOrder.setPackageStartTime(oldPackageOrder.getPackageStartTime());
        sysShopPackageOrder.setPackageEndTime(oldPackageOrder.getPackageEndTime());
        this.save(sysShopPackageOrder);
        return sysShopPackageOrder;
    }

    /**
     * ????????????????????????????????????
     *
     * @param buyShopPackage ?????????
     * @param shopInfo       ????????????
     * @return com.medusa.gruul.platform.api.entity.SysShopPackageOrder
     */
    private SysShopPackageOrder createPackageEntify(PlatformShopInfo shopInfo, SysShopPackage buyShopPackage) {
        SysShopPackageOrder sysShopPackageOrder = new SysShopPackageOrder();
        sysShopPackageOrder.setAccountId(shopInfo.getAccountId());
        sysShopPackageOrder.setShopTemplateInfoId(shopInfo.getShopTemplateId());
        sysShopPackageOrder.setOrderNum(IdUtil.createSnowflake(1, 2).nextIdStr());
        sysShopPackageOrder.setPackageId(buyShopPackage.getId());
        sysShopPackageOrder.setPackageData(JSONObject.toJSONString(buyShopPackage));
        sysShopPackageOrder.setPackagePriceUnit(buyShopPackage.getPackagePriceUnit());
        sysShopPackageOrder.setPackagePrice(buyShopPackage.getPackagePrice());
        sysShopPackageOrder.setStatus(CommonConstants.NUMBER_ZERO);
        sysShopPackageOrder.setShopName(shopInfo.getShopName());
        sysShopPackageOrder.setPackageName(buyShopPackage.getName());
        sysShopPackageOrder.setInvoiceStatus(CommonConstants.NUMBER_ZERO);
        PlatformShopTemplateInfo shopTemplateInfo = templateInfoService.getById(buyShopPackage.getTemplateId());
        sysShopPackageOrder.setTemplateName(shopTemplateInfo.getName());
        sysShopPackageOrder.setTenantId(shopInfo.getTenantId());
        return sysShopPackageOrder;
    }

    /**
     * ??????????????????
     *
     * @param buyShopPackage ?????????
     * @param shopInfo       ????????????
     * @param orderDto       ????????????
     * @return com.medusa.gruul.platform.api.entity.SysShopPackageOrder
     */
    private SysShopPackageOrder createOrderEntify(PlatformShopInfo shopInfo, SysShopPackage buyShopPackage, ShopPackageOrderDto orderDto) {
        //??????????????????????????????
        SysShopPackageOrder sysShopPackageOrder = createPackageEntify(shopInfo, buyShopPackage);
        //????????????????????????
        sysShopPackageOrder.setPackageTime(orderDto.getBuyPeriod());
        sysShopPackageOrder.setPayType(orderDto.getPayType());
        sysShopPackageOrder.setOrderSource(orderDto.getOrderSource());
        sysShopPackageOrder.setIsAgreed(orderDto.getAgreeProtocol());
        sysShopPackageOrder.setIsAutomaticDeduction(orderDto.getAutoDeduct());
        //??????????????????????????????
        if (orderDto.getPayType().equals(CommonConstants.NUMBER_FOUR)) {
            sysShopPackageOrder.setIsReceived(CommonConstants.NUMBER_ZERO);
            sysShopPackageOrder.setPayInfo(orderDto.getPayInfo());
            sysShopPackageOrder.setAuditorStatus(CommonConstants.NUMBER_ZERO);
            sysShopPackageOrder.setStatus(CommonConstants.NUMBER_ONE);
        }
        //????????????????????????
        if (StpAgentUtil.isLogin()) {
            sysShopPackageOrder.setAgentId(StpAgentUtil.getLoginIdAsLong());
        }
        return sysShopPackageOrder;
    }

    /**
     * ????????????
     *
     * @param buyShopPackage ?????????
     * @param shopInfo       ????????????
     * @param orderDto       ????????????
     */
    private SysShopPackageOrder order(SysShopPackage buyShopPackage, PlatformShopInfo shopInfo, ShopPackageOrderDto orderDto) {
        //???????????????????????????,????????????????????????????????????
        if (shopInfo.getPackageOrderId() != null && shopInfo.getIsDue().equals(CommonConstants.NUMBER_ZERO)) {
            //??????????????????????????????,??????????????????,?????????????????????????????????
            SysShopPackageOrder shopPackageOrder = this.getById(shopInfo.getPackageOrderId());
            if (shopPackageOrder != null && System.currentTimeMillis() >=
                    LocalDateTimeUtils.getMilliByTime(shopPackageOrder.getPackageEndTime())) {
                throw new ServiceException("????????????????????????????????????,????????????????????????");
            }
        }
        BigDecimal price = new BigDecimal("0.0");
        //???????????????????????????????????????
        if (!orderDto.getOrderSource().equals(CommonConstants.NUMBER_TWO)) {
            //?????????????????????
            price = calculatePrice(buyShopPackage, orderDto.getBuyPeriod());
        }
        //Todo ???????????????????????????????????????????????????
        SysShopPackageOrder sysShopPackageOrder = createOrderEntify(shopInfo, buyShopPackage, orderDto);
        sysShopPackageOrder.setOrderType(CommonConstants.NUMBER_ONE);
        sysShopPackageOrder.setAmountPayable(price);
        sysShopPackageOrder.setPaidPayable(price);
        sysShopPackageOrder.setPackageStartTime(LocalDateTime.now());
        LocalDateTime packageEndTime = LocalDateTimeUtil.offset(sysShopPackageOrder.getPackageStartTime(), orderDto.getBuyPeriod(), ChronoUnit.DAYS);
        sysShopPackageOrder.setPackageEndTime(packageEndTime);
        sysShopPackageOrder.setTenantId(shopInfo.getTenantId());

        this.save(sysShopPackageOrder);
        return sysShopPackageOrder;
    }

    /**
     * ????????????
     *
     * @param buyShopPackage ?????????
     * @param shopInfo       ????????????
     * @param orderDto       ????????????
     * @return com.medusa.gruul.platform.api.entity.SysShopPackageOrder
     */
    private SysShopPackageOrder pacakgeRenew(SysShopPackage buyShopPackage, PlatformShopInfo shopInfo, ShopPackageOrderDto orderDto) {
        Long shopInfoPackageId = shopInfo.getPackageId();
        if (ObjectUtil.isNull(shopInfoPackageId)) {
            throw new ServiceException("???????????????????????????");
        }
        SysShopPackageOrder shopPackageOrder = this.getById(shopInfo.getPackageOrderId());
        if (shopPackageOrder == null) {
            throw new ServiceException("???????????????");
        }
        SysShopPackage sysShopPackage = JSONObject.parseObject(shopPackageOrder.getPackageData(), SysShopPackage.class);
        //?????????????????????????????????????????????
        if (!buyShopPackage.getLevel().equals(sysShopPackage.getLevel())) {
            throw new ServiceException("???????????????????????????????????????????????????????????????");
        }

        //???????????????????????????
        if (LocalDateTimeUtils.getMilliByTime(shopPackageOrder.getPackageEndTime()) <= System.currentTimeMillis()) {
            throw new ServiceException("???????????????????????????");
        }

        //??????????????????????????????????????????
        //?????????????????????
        BigDecimal price = calculatePrice(buyShopPackage, orderDto.getBuyPeriod());
        //Todo ???????????????????????????????????????????????????
        SysShopPackageOrder order = createOrderEntify(shopInfo, buyShopPackage, orderDto);
        order.setOrderType(CommonConstants.NUMBER_TWO);
        order.setAmountPayable(price);
        order.setPaidPayable(price);
        //??????????????????????????????????????????
        order.setPackageStartTime(shopPackageOrder.getPackageStartTime());
        //??????????????????????????????????????????????????????
        LocalDateTime packageEndTime = LocalDateTimeUtil.offset(shopInfo.getDueTime(), orderDto.getBuyPeriod(), ChronoUnit.DAYS);
        order.setPackageEndTime(packageEndTime);
        order.setTenantId(shopInfo.getTenantId());
        this.save(order);
        return order;
    }

    /**
     * ????????????????????????
     *
     * @param buyShopPackage ??????
     * @param buyPeriod      ????????????
     * @return ????????????
     */
    private BigDecimal calculateActualPrice(SysShopPackage buyShopPackage, Integer buyPeriod) {
        //???????????????????????????????????????,???????????????????????? 1??????2??????3???
        //??????????????????????????????
        switch (buyShopPackage.getPackagePriceUnit()) {
            case 1:
                if (buyPeriod < 1) {
                    throw new ServiceException("??????????????????????????????:?????????????????????");
                }
                break;
            case 2:
                //??????????????????,???????????????365?????????
                if (buyPeriod % MeConstant.STATUS_365 == 0) {
                    int subPeriod = 5 * (buyPeriod / 365);
                    buyPeriod = buyPeriod - subPeriod;
                } else if (buyPeriod % MeConstant.STATUS_30 != 0) {
                    throw new ServiceException("??????????????????????????????:?????????????????????");
                }
                //???????????? 30???
                buyPeriod = buyPeriod / 30;
                break;
            case 3:
                if (buyPeriod % MeConstant.STATUS_365 != 0) {
                    throw new ServiceException("??????????????????????????????:?????????????????????");
                }
                buyPeriod = buyPeriod / 365;
                break;
            default:
                throw new ServiceException("??????????????????");
        }

        return NumberUtil.round(NumberUtil.mul(buyShopPackage.getPackagePrice(), buyPeriod), 2);
    }

    /**
     * ????????????????????????
     *
     * @param buyShopPackage ??????
     * @param buyPeriod      ????????????
     * @return ????????????  ??????null????????????????????????
     */
    private BigDecimal calculatePreferentialPrice(SysShopPackage buyShopPackage, Integer buyPeriod) {
        //????????????????????????
        String discountsJson = buyShopPackage.getDiscountsJson();
        if (StrUtil.isNotEmpty(discountsJson)) {
            JSONArray array = JSONObject.parseArray(discountsJson);
            for (Object o : array) {
                JSONObject jsonObject = (JSONObject) o;
                Integer value = jsonObject.getInteger("value");
                //??????????????????????????????????????????,?????????????????????????????????
                if (buyPeriod.equals(value)) {
                    return jsonObject.getBigDecimal("price");
                }
            }
        }
        return null;
    }

    /**
     * ????????????????????????
     *
     * @param buyShopPackage ??????
     * @param buyPeriod      ????????????
     * @return ???????????????
     */
    private BigDecimal calculatePrice(SysShopPackage buyShopPackage, Integer buyPeriod) {
        BigDecimal price = calculatePreferentialPrice(buyShopPackage, buyPeriod);
        if (price != null) {
            return price;
        }
        return calculateActualPrice(buyShopPackage, buyPeriod);
    }


    @Override
    public PageUtils<PackageOrderVo> orders(Integer page, Integer size, Integer status, String payStartTime, String payEndTime, String phone,
                                            String orderNum, String nikeName, Integer payType, Long templateId, Long userId) {
        IPage<SysShopPackageOrderDto> resultIpage = this.baseMapper.selectOrders(new Page<>(page, size),
                status, payStartTime, payEndTime, phone, orderNum, nikeName, payType, templateId, userId);

        if (CollectionUtil.isEmpty(resultIpage.getRecords())) {
            return new PageUtils(null, (int) resultIpage.getTotal(),
                    (int) resultIpage.getSize(), (int) resultIpage.getCurrent());
        }
        List<PackageOrderVo> orderVos = resultIpage.getRecords().stream().map(obj -> BeanUtil.toBean(obj, PackageOrderVo.class)).collect(Collectors.toList());
        return new PageUtils<>(orderVos, (int) resultIpage.getTotal(),
                (int) resultIpage.getSize(), (int) resultIpage.getCurrent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void orderOption(OrderOptionDto dto) {
        SysShopPackageOrder packageOrder = getById(dto.getOrderId());
        if (packageOrder == null) {
            throw new ServiceException("???????????????");
        }
        if (!CommonConstants.NUMBER_FOUR.equals(packageOrder.getPayType())) {
            throw new ServiceException("?????????????????????????????????");
        }
        if (!packageOrder.getStatus().equals(CommonConstants.NUMBER_ONE)) {
            throw new ServiceException("?????????????????????????????????");
        }
        //????????????
        if (dto.getOptionType().equals(CommonConstants.NUMBER_ONE)) {
            //?????????????????????
            packagePayNotify(packageOrder.getOrderNum());
            return;
        }
        //????????????
        if (dto.getOptionType().equals(CommonConstants.NUMBER_TWO)) {
            SysShopPackageOrder up = new SysShopPackageOrder();
            up.setId(packageOrder.getId());
            up.setStatus(CommonConstants.NUMBER_THREE);
            up.setAuditorStatus(CommonConstants.NUMBER_THREE);
            this.updateById(up);
            if (packageOrder.getOrderSource().equals(CommonConstants.NUMBER_FOUR)
                    && packageOrder.getAgentId() != null && packageOrder.getAgentId() > 0) {
            }
            return;
        }

    }

    @Override
    public PageUtils<PackageOrderConsoleVo> consoleOrders(Integer page, Integer size, Integer status, String payStartTime, String payEndTime, Long packageId, Integer platfromType, Integer selectType) {
        String tenantId = "";
        //???????????????????????????????????????????????????????????????????????????
        if (selectType.equals(CommonConstants.NUMBER_TWO)) {
            tenantId = TenantContextHolder.getTenantId();
        }
        List<Long> packageIds = null;
        if (packageId != null) {
            SysShopPackage sysShopPackage = sysShopPackageService.getById(packageId);
            List<SysShopPackage> sysShopPackages = sysShopPackageService.getByTeamplteId(sysShopPackage.getTemplateId());
            packageIds = sysShopPackages.stream().filter(obj -> obj.getLevel().equals(sysShopPackage.getLevel())).map(SysShopPackage::getId).collect(Collectors.toList());
        }
        List<Integer> orderSources = null;
        if (platfromType != null && platfromType > 0) {
            orderSources = new ArrayList<>(4);
            if (platfromType.equals(CommonConstants.NUMBER_ONE)) {
                orderSources.add(CommonConstants.NUMBER_ZERO);
            }
            if (platfromType.equals(CommonConstants.NUMBER_TWO)) {
                orderSources.add(CommonConstants.NUMBER_ONE);
                orderSources.add(CommonConstants.NUMBER_TWO);
                orderSources.add(CommonConstants.NUMBER_THREE);
                orderSources.add(CommonConstants.NUMBER_FOUR);
            }
        }
        IPage<SysShopPackageOrder> resultIpage = this.getBaseMapper().selectPage(new Page<>(page, size), new QueryWrapper<SysShopPackageOrder>()
                .eq("account_id", CurUserUtil.getPcRqeustAccountInfo().getUserId())
                .ge(CommonConstants.NUMBER_ZERO.equals(status), "status", CommonConstants.NUMBER_ONE)
                .eq(status > CommonConstants.NUMBER_ZERO, "status", status)
                .in(CollectionUtil.isNotEmpty(packageIds), "package_id", packageIds)
                .in(CollectionUtil.isNotEmpty(orderSources), "order_source", orderSources)
                .ge(StrUtil.isNotEmpty(payStartTime), "create_time", payStartTime)
                .le(StrUtil.isNotEmpty(payEndTime), "create_time", payEndTime)
                .eq(StrUtil.isNotEmpty(tenantId), "tenant_id", tenantId)
                .orderByDesc("create_time")
        );
        if (CollectionUtil.isEmpty(resultIpage.getRecords())) {
            return new PageUtils(null, (int) resultIpage.getTotal(),
                    (int) resultIpage.getSize(), (int) resultIpage.getCurrent());
        }
        List<Long> orderIds = resultIpage.getRecords().stream().filter(obj -> CommonConstants.NUMBER_ONE.equals(obj.getInvoiceStatus()))
                .map(SysShopPackageOrder::getId).collect(Collectors.toList());
        Map<Long, InvoiceOrderApplyVo> invoiceOrderApplyVosMap = new HashMap<>(0);
        if (CollectionUtil.isNotEmpty(orderIds)) {
            List<InvoiceOrderApplyVo> invoiceOrderApplyVos = sysShopInvoiceOrderService.getByOrderTypeAndOrderIds(CommonConstants.NUMBER_TWO, orderIds);
            if (CollectionUtil.isNotEmpty(invoiceOrderApplyVos)) {
                invoiceOrderApplyVosMap = invoiceOrderApplyVos.stream().collect(Collectors.toMap(InvoiceOrderApplyVo::getOrderId, v -> v));
            }
        }
        Map<Long, InvoiceOrderApplyVo> finalOrderApplyVoMap = invoiceOrderApplyVosMap;
        List<PackageOrderConsoleVo> orderVos = resultIpage.getRecords().stream().map(obj -> {
            PackageOrderConsoleVo packageOrderConsoleVo = BeanUtil.toBean(obj, PackageOrderConsoleVo.class);
            packageOrderConsoleVo.setInvoiceOrderApplyVo(finalOrderApplyVoMap.get(obj.getId()));
            return packageOrderConsoleVo;
        }).collect(Collectors.toList());

        return new PageUtils(orderVos, (int) resultIpage.getTotal(),
                (int) resultIpage.getSize(), (int) resultIpage.getCurrent());
    }

    @Override
    public Boolean orderPayIfOk(Long orderId) {
        SysShopPackageOrder packageOrder = this.getById(orderId);
        if (packageOrder == null) {
            throw new ServiceException("??????????????????");
        }
        if (packageOrder.getStatus().equals(CommonConstants.NUMBER_TWO)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }


}
