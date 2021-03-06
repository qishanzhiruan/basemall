package com.medusa.gruul.logistics.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.additional.update.impl.LambdaUpdateChainWrapper;
import com.medusa.gruul.common.core.constant.CommonConstants;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.core.util.DateUtils;
import com.medusa.gruul.common.core.util.Result;
import com.medusa.gruul.common.core.util.SystemCode;
import com.medusa.gruul.logistics.api.entity.LogisticsAddress;
import com.medusa.gruul.logistics.api.entity.LogisticsCompany;
import com.medusa.gruul.logistics.api.entity.LogisticsShop;
import com.medusa.gruul.logistics.mapper.*;
import com.medusa.gruul.logistics.model.dto.manager.LogisticsAddressDto;
import com.medusa.gruul.logistics.model.dto.manager.LogisticsBatchDeliverDto;
import com.medusa.gruul.logistics.model.dto.manager.LogisticsPrintDeliverDto;
import com.medusa.gruul.logistics.model.dto.manager.express.ExpressInfoDto;
import com.medusa.gruul.logistics.model.enums.AddressDefaultEnum;
import com.medusa.gruul.logistics.model.enums.AddressTypeEnum;
import com.medusa.gruul.logistics.model.param.LogisticsAddressParam;
import com.medusa.gruul.logistics.model.param.LogisticsExpressPrintParam;
import com.medusa.gruul.logistics.model.vo.*;
import com.medusa.gruul.logistics.service.ILogisticsAddressService;
import com.medusa.gruul.logistics.util.express.kuaidihelp.KuaiDiHelp;
import com.medusa.gruul.logistics.util.express.sf.SFExpressUtil;
import com.medusa.gruul.order.api.entity.OrderDelivery;
import com.medusa.gruul.order.api.entity.OrderSetting;
import com.medusa.gruul.order.api.feign.RemoteOrderService;
import com.medusa.gruul.order.api.model.OrderDeliveryDto;
import com.medusa.gruul.order.api.model.OrderItemVo;
import com.medusa.gruul.order.api.model.OrderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ??????????????????????????????
 * @author zhaozheng
 */
@Service
@Slf4j
public class LogisticsAddressServiceImpl implements ILogisticsAddressService {

	@Autowired
	private LogisticsAddressMapper logisticsAddressMapper;
	@Resource
	private LogisticsShopMapper logisticsShopMapper;
	@Resource
	private LogisticsCompanyMapper logisticsCompanyMapper;
	@Resource
	private RemoteOrderService remoteOrderService;
	@Autowired
	private LogisticsExpressPrintMapper logisticsExpressPrintMapper;
	@Autowired
	private LogisticsExpressAddressMapper logisticsExpressAddressMapper;


	/**
	 * ???????????????????????????
	 * @param logisticsAddressParam
	 * @return IPage<LogisticsAddressVo>
	 */
	@Override
	public IPage<LogisticsAddressVo> getAddressList(LogisticsAddressParam logisticsAddressParam) {
		IPage<LogisticsAddressVo> page = new Page<>(logisticsAddressParam.getCurrent(),
				logisticsAddressParam.getSize());
		List<LogisticsAddressVo> logisticsAddressVos = this.logisticsAddressMapper
				.queryLogisticsAddressList(page, logisticsAddressParam);
		return page.setRecords(logisticsAddressVos);
	}

	/**
	 * ??????????????????
	 * @return List<LogisticsAddressVo>
	 */
	@Override
	public List<LogisticsAddressVo> getAllAddressList() {
		List<LogisticsAddressVo> logisticsAddressVos = this.logisticsAddressMapper.queryAllLogisticsAddressList();
		return logisticsAddressVos;
	}

	/**
	 * ??????/?????? ??????
	 * @param logisticsAddressDto
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setAddress(LogisticsAddressDto logisticsAddressDto) {
		// ??????,????????????
		if (logisticsAddressDto.getId() == null) {
			//?????????????????????????????????????????? ???????????????
			int logisticsAddressSearch = this.logisticsAddressMapper.selectCount(
					new QueryWrapper<LogisticsAddress>().eq("name", logisticsAddressDto.getName())
							.eq("province_id", logisticsAddressDto.getProvinceId())
							.eq("city_id", logisticsAddressDto.getCityId())
							.eq("country_id", logisticsAddressDto.getCountryId())
							.eq("address", logisticsAddressDto.getAddress())
							.eq("zip_code", logisticsAddressDto.getZipCode())
							.eq("phone", logisticsAddressDto.getPhone()));
			if (logisticsAddressSearch > 0) {
				throw new ServiceException("???????????????????????????", SystemCode.DATA_ADD_FAILED.getCode());
			}
			//?????? ????????????????????????  ??????????????????????????????????????????
			int count = this.logisticsAddressMapper.selectCount(new QueryWrapper<>());
			LogisticsAddress logisticsAddress = logisticsAddressDto.coverBean();
			if (count == 0) {
				logisticsAddress.setDefSend(AddressDefaultEnum.YES.getAddressDefault());
				logisticsAddress.setDefReceive(AddressDefaultEnum.YES.getAddressDefault());
			}
			int insert = this.logisticsAddressMapper.insert(logisticsAddress);
			if (insert == 0) {
				throw new ServiceException("?????????????????????", SystemCode.DATA_ADD_FAILED.getCode());
			}
		} else {
			//??????
			LogisticsAddress logisticsAddress = this.logisticsAddressMapper.selectById(logisticsAddressDto.getId());
			if (BeanUtil.isEmpty(logisticsAddress)) {
				throw new ServiceException("??????????????????", SystemCode.DATA_EXISTED.getCode());
			}
			//?????????????????????????????????????????? ???????????????
			int logisticsAddressSearch = this.logisticsAddressMapper.selectCount(
					new QueryWrapper<LogisticsAddress>().eq("name", logisticsAddressDto.getName())
							.eq("province_id", logisticsAddressDto.getProvinceId())
							.eq("city_id", logisticsAddressDto.getCityId())
							.eq("country_id", logisticsAddressDto.getCountryId())
							.eq("address", logisticsAddressDto.getAddress())
							.eq("zip_code", logisticsAddressDto.getZipCode())
							.eq("phone", logisticsAddressDto.getPhone()).ne("id", logisticsAddressDto.getId()));
			if (logisticsAddressSearch > 0) {
				throw new ServiceException("???????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
			}
			logisticsAddress.setName(logisticsAddressDto.getName());
			logisticsAddress.setAddress(logisticsAddressDto.getAddress());
			logisticsAddress.setPhone(logisticsAddressDto.getPhone());
			logisticsAddress.setZipCode(logisticsAddressDto.getZipCode());
			logisticsAddress.setProvince(logisticsAddressDto.getProvince());
			logisticsAddress.setProvinceId(logisticsAddressDto.getProvinceId());
			logisticsAddress.setCity(logisticsAddressDto.getCity());
			logisticsAddress.setCityId(logisticsAddressDto.getCityId());
			logisticsAddress.setCountry(logisticsAddressDto.getCountry());
			logisticsAddress.setCountryId(logisticsAddressDto.getCountryId());
			int update = this.logisticsAddressMapper.updateById(logisticsAddress);
			if (update == 0) {
				throw new ServiceException("?????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
			}
		}
	}

	/**
	 * ?????? ????????????
	 * @param type 1-???????????? 2-????????????
	 * @param id
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setDefAddress(Integer type, Long id) {
		if (type == AddressTypeEnum.DEFSEND.getAddressType()) {
			//??????????????????????????? ????????????????????????????????????
			boolean updateSign = new LambdaUpdateChainWrapper<>(logisticsAddressMapper)
					.eq(LogisticsAddress::getDefSend, AddressDefaultEnum.YES.getAddressDefault())
					.set(LogisticsAddress::getDefSend, AddressDefaultEnum.NO.getAddressDefault()).update();
			if (!updateSign) {
				throw new ServiceException("?????????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
			} else {
				//?????????????????????
				boolean sign = new LambdaUpdateChainWrapper<>(logisticsAddressMapper).eq(LogisticsAddress::getId, id)
						.set(LogisticsAddress::getDefSend, AddressDefaultEnum.YES.getAddressDefault()).update();
				if (!sign) {
					throw new ServiceException("?????????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
				}
			}
		} else {
			//??????????????????????????? ????????????????????????????????????
			boolean updateSign = new LambdaUpdateChainWrapper<>(logisticsAddressMapper)
					.eq(LogisticsAddress::getDefReceive, AddressDefaultEnum.YES.getAddressDefault())
					.set(LogisticsAddress::getDefReceive, AddressDefaultEnum.NO.getAddressDefault()).update();
			if (!updateSign) {
				throw new ServiceException("?????????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
			} else {
				//?????????????????????
				boolean sign = new LambdaUpdateChainWrapper<>(logisticsAddressMapper).eq(LogisticsAddress::getId, id)
						.set(LogisticsAddress::getDefReceive, AddressDefaultEnum.YES.getAddressDefault()).update();
				if (!sign) {
					throw new ServiceException("?????????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
				}
			}

		}

	}

	/**
	 * ????????????
	 * @param id ??????
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delAddress(Long id) {
		LogisticsAddress logisticsAddress = this.logisticsAddressMapper.selectById(id);
		if (BeanUtil.isEmpty(logisticsAddress)) {
			throw new ServiceException("??????????????????", SystemCode.DATA_EXISTED.getCode());
		} else {
			//????????????????????????/???????????? ????????????????????????
			if (AddressDefaultEnum.YES.getAddressDefault() == logisticsAddress.getDefReceive()
					|| AddressDefaultEnum.YES.getAddressDefault() == logisticsAddress.getDefSend()) {
				throw new ServiceException("???????????????????????????", SystemCode.DATA_DELETE_FAILED.getCode());
			} else {
				int delete = this.logisticsAddressMapper.deleteById(id);
				if (delete == 0) {
					throw new ServiceException("?????????????????????", SystemCode.DATA_DELETE_FAILED.getCode());
				}
			}
		}
	}

	/**
	 * @param type ??????????????? 1-???????????? 2-????????????
	 * ???????????????/????????????
	 */
	@Override
	public LogisticsAddressVo getDefaultAddress(Integer type) {
		try {
			LogisticsAddressVo logisticsAddress = this.logisticsAddressMapper.queryDefaultAddress(type);
			if (BeanUtil.isEmpty(logisticsAddress)) {
				throw new ServiceException("??????????????????,????????????", SystemCode.DATA_DELETE_FAILED.getCode());
			}
			return logisticsAddress;
		} catch (Exception e) {
			throw new ServiceException("??????????????????,????????????????????????", SystemCode.DATA_DELETE_FAILED.getCode());
		}
	}

	/**
	 *  ????????????????????????????????????
	 * @param shopId
	 * @param tenantId
	 */
	@Override
	public Map<String, Object> listLogisticsCompany(String shopId, String tenantId) {
		Map<String, Object> res = new HashMap<>(CommonConstants.NUMBER_TWO);
		Map<String, Object> param = new HashMap<>(CommonConstants.NUMBER_THREE);
		param.put("shopId", shopId);
		param.put("tenantId", tenantId);
		List<LogisticsCompany> logisticsCompanies = logisticsCompanyMapper.selectListCompany(param);
		List<LogisticsCompanyVo> logisticsCompanyVos = new ArrayList<>(logisticsCompanies.size());
		LogisticsShop logisticsShop = logisticsShopMapper
				.selectOne(new QueryWrapper<LogisticsShop>().eq("shop_id", shopId).eq("is_default", 1));
		if (CollectionUtil.isNotEmpty(logisticsCompanies)) {
			logisticsCompanies.forEach(logisticsCompany -> {
				LogisticsCompanyVo logisticsCompanyVo = new LogisticsCompanyVo();
				logisticsCompanyVo.setCode(logisticsCompany.getCode());
				logisticsCompanyVo.setName(logisticsCompany.getName());
				logisticsCompanyVo.setIsDefault(CommonConstants.NUMBER_ZERO);
				logisticsCompanyVo.setLogisticsCompanyId(logisticsCompany.getId());
				if ((null != logisticsShop) && (logisticsCompany.getId()
						.equals(logisticsShop.getLogisticsCompanyId()))) {
					logisticsCompanyVo.setIsDefault(CommonConstants.NUMBER_ONE);
				}
				List<LogisticsExpressAddressVo> logisticsExpressAddressVos = logisticsExpressAddressMapper
						.queryByExpressCode(logisticsCompany.getCode());
				logisticsCompanyVo.setLogisticsExpressAddressVos(logisticsExpressAddressVos);
				logisticsCompanyVos.add(logisticsCompanyVo);
			});
		}
		//???????????????
		List<LogisticsExpressPrintVo> logisticsExpressPrintVos = this.logisticsExpressPrintMapper
				.queryLogisticsExpressPrintList(null, new LogisticsExpressPrintParam());
		res.put("LogisticsPrinterVos", logisticsExpressPrintVos);
		res.put("logisticsCompanyVos", logisticsCompanyVos);

		return res;
	}

	/**
	 *  ??????????????????????????????
	 * @param logisticsCompanyId
	 */
	@Override
	public void setCompanyDefault(Long logisticsCompanyId) {
		LogisticsShop logisticsShop = logisticsShopMapper
				.selectOne(new QueryWrapper<LogisticsShop>().eq("is_default", CommonConstants.NUMBER_ONE));
		if (BeanUtil.isEmpty(logisticsShop)) {
			logisticsShop = new LogisticsShop();
			logisticsShop.setIsDefault(CommonConstants.NUMBER_ONE);
			logisticsShop.setLogisticsCompanyId(logisticsCompanyId);
			logisticsShop.setCreateTime(DateUtils.timestampCoverLocalDateTime(System.currentTimeMillis()));
			int insert = logisticsShopMapper.insert(logisticsShop);
			if(insert == 0){
				throw new ServiceException("???????????????", SystemCode.DATA_ADD_FAILED.getCode());
			}
		} else {
			logisticsShop.setUpdateTime(DateUtils.timestampCoverLocalDateTime(System.currentTimeMillis()));
			logisticsShop.setLogisticsCompanyId(logisticsCompanyId);
			int update = logisticsShopMapper.updateById(logisticsShop);
			if(update == 0){
				throw new ServiceException("???????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
			}
		}
	}

	/**
	 *  ??????????????????????????? (??????????????????????????????)
	 * @param logisticsPrintDeliverDto ???????????????dto
	 */
	@Override
    @Transactional(rollbackFor = Exception.class)
	public void doPrintDeliverGoods(LogisticsPrintDeliverDto logisticsPrintDeliverDto) {
        OrderSetting orderSetting = remoteOrderService.getOrderSetting();
		if (StrUtil.isEmpty(orderSetting.getKdAppKey())){
			throw new ServiceException("??????????????????KEY");
		}
        if (StrUtil.isEmpty(orderSetting.getKdAppId())){
            throw new ServiceException("??????????????????ID");
        }
		//?????????????????????????????????
		Map<String, Object> param = new HashMap<>(CommonConstants.NUMBER_THREE);
		List<OrderDeliveryDto> orderDeliveryDtos = new ArrayList<>(CommonConstants.NUMBER_ONE);
		param.put("code", logisticsPrintDeliverDto.getDeliverCode());
		param.put("shopId", logisticsPrintDeliverDto.getShopId());
		param.put("tenantId", logisticsPrintDeliverDto.getTenantId());
		LogisticsCompany logisticsCompany = logisticsCompanyMapper.selectListCompanyByParam(param);
		if (null == logisticsCompany) {
			throw new ServiceException("???????????????????????????");
		}
		List<Long> orderIds = logisticsPrintDeliverDto.getOrderIds();
		if (CollectionUtil.isEmpty(orderIds)) {
			throw new ServiceException("??????id?????????");
		}
		orderIds.forEach(orderId -> {
			OrderDeliveryDto orderDeliveryDto = new OrderDeliveryDto();
			orderDeliveryDto.setOrderId(orderId);
			orderDeliveryDto.setDeliveryCompany(logisticsCompany.getName());
			orderDeliveryDto.setDeliveryCode(logisticsCompany.getCode());
			String resultList = getExpressInfoDto(logisticsPrintDeliverDto, logisticsCompany.getCode(), orderId);
			Map<String, String> resultMap = (Map<String, String>) JSON.parse(resultList);
			if(MapUtil.isEmpty(resultMap)){
				throw new ServiceException("???????????????????????????");
			}
			orderDeliveryDto.setDeliverySn(resultMap.get("waybill_no"));
			orderDeliveryDto.setSortingCode(resultMap.get("sorting_code"));
			orderDeliveryDto.setPackageName(resultMap.get("package_name"));
			orderDeliveryDto.setPackageCode(resultMap.get("package_code"));
			RoutingInfoVo routingInfoVo = new RoutingInfoVo();
			routingInfoVo.setWaybillCode(resultMap.get("waybill_no"));
			routingInfoVo.setSortingCode(resultMap.get("sorting_code"));
			routingInfoVo.setPackageName(resultMap.get("package_name"));
			routingInfoVo.setPackageCode(resultMap.get("package_code"));
			//??????????????????
			expressPrint(logisticsPrintDeliverDto, logisticsCompany.getCode(), routingInfoVo, orderId);
			orderDeliveryDtos.add(orderDeliveryDto);

		});
		//??????
		int i = remoteOrderService.doLogisticsOrderDelivery(JSON.toJSONString(orderDeliveryDtos));
		if(i < CommonConstants.NUMBER_ONE){
			throw new ServiceException("????????????");
		}
		log.info("????????????????????????????????? ??? " + i);
	}

	/**
	 * ??????????????????
	 * @param logisticsPrintDeliverDto
	 * @param code
	 * @param orderId
	 */
	private String getExpressInfoDto(LogisticsPrintDeliverDto logisticsPrintDeliverDto, String code, Long orderId) {
		ExpressInfoDto expressInfoDto;
		expressInfoDto = convertDelivery(code, orderId);
		Result logisticsExpressNumber = getLogisticsExpressNumber(expressInfoDto, logisticsPrintDeliverDto.getExpressId(),
						logisticsPrintDeliverDto.getShopId());
		if(logisticsExpressNumber.getCode() == CommonConstants.SUCCESS){
			return (String) logisticsExpressNumber.getData();
		}else{
			//{"code":300115,"msg":"??????????????????????????????","data":{"reason":"????????????????????????"}}
			if ("??????????????????????????????".equals(logisticsExpressNumber.getMsg())){
				throw  new ServiceException("???????????????????????????????????????");
			}
			throw new ServiceException(logisticsExpressNumber.getMsg(), SystemCode.FAILURE.getCode());
		}

	}

	/**
	 * ??????????????????????????????????????????
	 * ??????????????????????????????????????????????????????????????????
	 * @param expressInfoDto ??????????????????
	 * @param expressId ???????????????id
	 */
	@Override
	public Result getLogisticsExpressNumber(ExpressInfoDto expressInfoDto, Long expressId,String shopId) {
		log.info("getLogisticsExpressNumber {} ",JSON.toJSONString(expressInfoDto),expressId,shopId);
		LogisticsExpressAddressVo logisticsExpressAddressVo = this.logisticsExpressAddressMapper.queryByExpressId(expressId);
		if(BeanUtil.isEmpty(logisticsExpressAddressVo)){
			return Result.failed(String.valueOf("????????????????????????????????????"));
		}
		try{
			String data;
			if(KuaiDiHelp.SFCODE.equals(expressInfoDto.getShipperType())){
				log.info("sfparam {},{}",JSON.toJSONString(expressInfoDto),JSON.toJSONString(logisticsExpressAddressVo));
				cn.hutool.json.JSONObject sfNo = SFExpressUtil.getSFNo(expressInfoDto, logisticsExpressAddressVo);
				cn.hutool.json.JSONObject response = sfNo.getJSONObject("Response");
				String head =(String) response.get("Head");
				log.info("sfres {}",head);
				if("OK".equals(head)){
					Map<String, String> resultMap = new HashMap(CommonConstants.NUMBER_FOUR);
					cn.hutool.json.JSONObject jsonResult =  response.getJSONObject("Body").getJSONObject("OrderResponse");
					resultMap.put("waybill_no", String.valueOf(jsonResult.get("mailno")));
					resultMap.put("sorting_code", String.valueOf(jsonResult.getJSONObject("rls_info").getJSONObject("rls_detail").get("twoDimensionCode")));
					resultMap.put("package_name", String.valueOf(jsonResult.get("origincode")));
					resultMap.put("package_code", String.valueOf(jsonResult.get("dest_code")));
					data = JSON.toJSONString(resultMap);
					return Result.ok(data);
				}else {
					cn.hutool.json.JSONObject error = response.getJSONObject("ERROR");
					return Result.failed(String.valueOf(error.get("content")));
				}
			}else{
				OrderSetting orderSetting = remoteOrderService.getOrderSetting();
				String kdAppId = orderSetting.getKdAppId();
				String kdAppKey = orderSetting.getKdAppKey();
				log.info("financeOrderSetting kdAppId {} kdAppKey{}",kdAppId,kdAppKey);
				data = KuaiDiHelp.getExpressNo(expressInfoDto, logisticsExpressAddressVo, kdAppId, kdAppKey);
				log.info("KuaiDiHelp data {} ",data);
				JSONObject jsonObject = JSON.parseObject(data);
				if(String.valueOf(jsonObject.get("code")).equals(CommonConstants.NUMBER_ZERO.toString())) {
					Map<String, String> resultMap = new HashMap(CommonConstants.NUMBER_FOUR);
					JSONObject jsonData= jsonObject.getJSONObject("data");
					JSONObject jsonResult= jsonData.getJSONObject("result");
					resultMap.put("waybill_no", String.valueOf(jsonResult.get("waybill_no")));
					resultMap.put("sorting_code", String.valueOf(jsonResult.get("sorting_code")));
					resultMap.put("package_name", String.valueOf(jsonResult.get("package_name")));
					resultMap.put("package_code", String.valueOf(jsonResult.get("package_code")));
					data = JSON.toJSONString(resultMap);
					return Result.ok(data);
				}else {
					return Result.failed(String.valueOf(jsonObject.get("msg")));
				}
			}
		}catch(Exception e){
			log.error(e.getMessage());
			return Result.failed("???????????????????????????");
		}
	}

	private ExpressInfoDto convertDelivery(String code, Long orderId) {
		ExpressInfoDto expressInfoDto = new ExpressInfoDto();
		expressInfoDto.setOrderId(String.valueOf(orderId));
		expressInfoDto.setShipperType(code);
		OrderVo orderVo = remoteOrderService.orderInfo(orderId);
		log.info(" remoteOrderService.orderInfo {}", JSON.toJSONString(orderVo));
		OrderDelivery orderDelivery = orderVo.getOrderDelivery();
		expressInfoDto.setName(orderDelivery.getReceiverName());
		expressInfoDto.setMobile(orderDelivery.getReceiverPhone());
		expressInfoDto.setProvince(orderDelivery.getReceiverProvince());
		expressInfoDto.setCity(orderDelivery.getReceiverCity());
		expressInfoDto.setDistrict(orderDelivery.getReceiverRegion());
		expressInfoDto.setAddress(orderDelivery.getReceiverDetailAddress());

		List<OrderItemVo> orderItemList = orderVo.getOrderItemList();
		expressInfoDto.setTradeName(orderItemList.get(0).getProductName());
		List<String> itemNames = new ArrayList<>(orderItemList.size());
		orderItemList.forEach(orderItem -> {
			itemNames.add(orderItem.getProductName());
		});
		return expressInfoDto;
	}
	/**
	 * ????????????
	 * @param logisticsPrintDeliverDto
	 * @param code
	 * @param routingInfoVo
	 * @param orderId
	 */
	private void expressPrint(LogisticsPrintDeliverDto logisticsPrintDeliverDto, String code, RoutingInfoVo routingInfoVo,
			Long orderId) {
		ExpressInfoDto expressInfoDto;
		//??????????????????????????????????????????????????????
		LogisticsExpressAddressVo logisticsExpressAddressVo = this.logisticsExpressAddressMapper
				.queryByExpressId(logisticsPrintDeliverDto.getExpressId());
		if (null == logisticsExpressAddressVo) {
			throw new ServiceException("????????????????????????????????????", SystemCode.DATA_ADD_FAILED.getCode());
		}
		expressInfoDto = convertDelivery(code, orderId);
		//?????????????????????id?????????
		OrderSetting orderSetting = remoteOrderService.getOrderSetting();
		String kdAppId = orderSetting.getKdAppId();
		String kdAppKey = orderSetting.getKdAppKey();
		log.info("expressInfoDto {} ", JSON.toJSONString(expressInfoDto));
		log.info("financeOrderSetting {} ", JSON.toJSONString(orderSetting));
		log.info("logisticsExpressAddressVo {} ", JSON.toJSONString(logisticsExpressAddressVo));
		log.info("routingInfoVo {} ", routingInfoVo);
		log.info("printCode {} ", logisticsPrintDeliverDto.getPrintCode());
		String expressPrint = KuaiDiHelp
				.getExpressPrint(expressInfoDto, logisticsExpressAddressVo, kdAppId, kdAppKey, routingInfoVo,
						logisticsPrintDeliverDto.getPrintCode());
		JSONObject jsonObject = JSON.parseObject(expressPrint);
		if (jsonObject.isEmpty() || !CommonConstants.STATUS_NORMAL.equals(String.valueOf(jsonObject.get("code")))) {
			throw new ServiceException(String.valueOf(jsonObject.get("msg")), SystemCode.DATA_ADD_FAILED.getCode());
		}
	}

	/**
	 * ????????????
	 * @param shopId
	 * @param tenantId
	 * @param logisticsBatchDeliverDtos
	 */
	@Override
	public void doBatchDeliver(List<LogisticsBatchDeliverDto> logisticsBatchDeliverDtos, String shopId,
			String tenantId) {
		if (null == logisticsBatchDeliverDtos || CommonConstants.NUMBER_ZERO == logisticsBatchDeliverDtos.size()) {
			throw new ServiceException("????????????????????????");
		}
		List<OrderDeliveryDto> list = new ArrayList<>(logisticsBatchDeliverDtos.size());
		logisticsBatchDeliverDtos.forEach(logisticsDeliverDto -> {
			Map<String, Object> param = new HashMap<>(CommonConstants.NUMBER_THREE);
			param.put("code", logisticsDeliverDto.getDeliverCode());
			param.put("shopId", shopId);
			param.put("tenantId", tenantId);
			LogisticsCompany logisticsCompany = logisticsCompanyMapper.selectListCompanyByParam(param);
			OrderDeliveryDto orderDeliveryDto = new OrderDeliveryDto();
			orderDeliveryDto.setOrderId(logisticsDeliverDto.getOrderId());
			orderDeliveryDto.setDeliveryCompany(logisticsCompany.getName());
			orderDeliveryDto.setDeliveryCode(logisticsDeliverDto.getDeliverCode());
			orderDeliveryDto.setDeliverySn(logisticsDeliverDto.getLogisticsCode());
			orderDeliveryDto.setOrderId(logisticsDeliverDto.getOrderId());
			list.add(orderDeliveryDto);
		});
		remoteOrderService.doLogisticsOrderDelivery(JSON.toJSONString(list));
	}
}
