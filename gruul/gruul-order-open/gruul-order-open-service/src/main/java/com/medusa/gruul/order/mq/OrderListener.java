package com.medusa.gruul.order.mq;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.medusa.gruul.common.core.constant.TimeConstants;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.data.tenant.ShopContextHolder;
import com.medusa.gruul.common.data.tenant.TenantContextHolder;
import com.medusa.gruul.common.dto.CurUserDto;
import com.medusa.gruul.goods.api.constant.GoodsSkuStockRedisKey;
import com.medusa.gruul.goods.api.entity.SkuStock;
import com.medusa.gruul.goods.api.feign.RemoteGoodsService;
import com.medusa.gruul.order.api.constant.OrderFailedRedisKey;
import com.medusa.gruul.order.api.constant.OrderQueueNameConstant;
import com.medusa.gruul.order.api.model.*;
import com.medusa.gruul.order.service.IMiniOrderService;
import com.medusa.gruul.order.service.IOrderDeliveryService;
import com.medusa.gruul.order.service.IOrderSettingService;
import com.medusa.gruul.order.service.IOrderShareSettingService;
import com.medusa.gruul.payment.api.model.dto.RefundNotifyResultDto;
import com.medusa.gruul.payment.api.model.dto.WxPayNotifyResultDto;
import com.medusa.gruul.platform.api.constant.ExchangeConstant;
import com.medusa.gruul.platform.api.constant.QueueNameConstant;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Receiver.java
 *
 * @author alan
 * @date 2019/10/6 13:49
 */
@Slf4j
@Component
public class OrderListener {
    @Resource
    private IMiniOrderService orderService;
    @Resource
    private IOrderSettingService orderSettingService;
    @Resource
    private IOrderShareSettingService orderShareSettingService;
    @Resource
    private IOrderDeliveryService orderDeliveryService;
    @Resource
    private RemoteGoodsService remoteGoodsService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * ???????????????
     *
     * @param jsonStr
     * @return void
     * @author alan
     * @date 2019/12/9 20:20
     */
    @RabbitListener(queues = OrderQueueNameConstant.DATA_INIT)
    public void dataInit(String jsonStr, Channel channel, Message m) throws IOException {
        log.info("???????????????????????????:deliveryTag{},????????????{},????????????{}.", m.getMessageProperties().getDeliveryTag(), DateUtil.now(),
                jsonStr);
        try {
            DataInitMessage dataInitMessage = JSONObject.parseObject(jsonStr, DataInitMessage.class);
            TenantContextHolder.setTenantId(dataInitMessage.getTenantId());
            ShopContextHolder.setShopId(dataInitMessage.getShopId());
            orderSettingService.init();
            orderShareSettingService.init();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        channel.basicAck(m.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * ?????????????????????
     *
     * @param orderMessage
     * @param channel
     * @param message
     * @return void
     * @author alan
     * @date 2019/12/9 20:20
     */
    @RabbitListener(queues = OrderQueueNameConstant.ORDER_CREATE)
    public void createOrder(CreateOrderMessage orderMessage, Channel channel, Message message) throws IOException {
        log.info("????????????????????????:deliveryTag{},????????????{},????????????{}.", message.getMessageProperties().getDeliveryTag(),
                DateUtil.now(),
                orderMessage.toString());
        try {
            CreateOrderDto createOrderDto = orderMessage.getOrderVo();
            Long orderId = orderMessage.getOrderId();
            CurUserDto curUser = orderMessage.getCurUser();
            TenantContextHolder.setTenantId(orderMessage.getTenantId());
            ShopContextHolder.setShopId(orderMessage.getCurUser().getShopId());
            List<ItemDto> itemDtoList = createOrderDto.getItemDtoList();
            List<SkuStock> skuStockList = new ArrayList<>(itemDtoList.size());
            OrderFailedRedisKey orderFailed = new OrderFailedRedisKey();
            log.info("sku is {}", itemDtoList.size());
            for (ItemDto itemDto : itemDtoList) {
                //????????????
                log.info("search skuStock {} start", itemDto.getSkuId());
                SkuStock skuStock = remoteGoodsService.findSkuStockById(itemDto.getSkuId());
                if (ObjectUtil.isNull(skuStock)) {
                    orderFailed.setNxPx(orderId.toString(), "????????????????????????", TimeConstants.ONE_DAY);
                    throw new ServiceException("????????????????????????");
                }
                log.info("skuStock is {}", skuStock.toString());

                int stock = skuStock.getStock();
                if (stock <= 0) {
                    new GoodsSkuStockRedisKey().set(itemDto.getSkuId().toString(), stock + "");
                    orderFailed.setNxPx(orderId.toString(), "???????????????", TimeConstants.ONE_DAY);
                    throw new ServiceException("???????????????");
                }
                skuStockList.add(skuStock);

            }
            //????????? ????????? ????????????
            Boolean succ = orderService.createOrder(createOrderDto, orderId, skuStockList, curUser);
            if (succ) {
                log.info("????????????????????????: deliveryTag{}", message.getMessageProperties().getDeliveryTag());
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), true);
            } else {
                throw new ServiceException("??????????????????");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.info("????????????????????????: deliveryTag{}", message.getMessageProperties().getDeliveryTag());

            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
        }
    }


    /**
     * ??????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2019/12/9 20:20
     */
    @RabbitListener(queues = OrderQueueNameConstant.ORDER_AUTO_CANCEL)
    public void autoCancelOrder(BaseOrderMessage message, MessageProperties properties, Channel channel) throws IOException {
        log.info("??????????????????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());

        try {
            TenantContextHolder.setTenantId(message.getTenantId());
            ShopContextHolder.setShopId(message.getShopId());
            orderService.autoCancelOrder(message.getOrderId());
            log.info("??????????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            channel.basicAck(properties.getDeliveryTag(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * ???????????????????????????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2019/12/9 20:21
     */
    @RabbitListener(queues = OrderQueueNameConstant.ORDER_AUTO_RECEIPT)
    public void autoReceiptOrder(BaseOrderMessage message, MessageProperties properties, Channel channel) throws IOException {
        log.info("????????????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());
        try {
            TenantContextHolder.setTenantId(message.getTenantId());
            ShopContextHolder.setShopId(message.getShopId());
            orderService.receiptOrder(message.getOrderId(), true);
            log.info("??????????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            channel.basicAck(properties.getDeliveryTag(), true);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * ??????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2019/12/9 20:20
     */
    @RabbitListener(queues = OrderQueueNameConstant.ORDER_AUTO_COMPLETED)
    public void autoCompleteOrder(BaseOrderMessage message, MessageProperties properties, Channel channel) throws IOException {
        log.info("??????????????????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());
        try {
            TenantContextHolder.setTenantId(message.getTenantId());
            ShopContextHolder.setShopId(message.getShopId());
            orderService.evaluateOrder(message.getOrderId());
            log.info("??????????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            channel.basicAck(properties.getDeliveryTag(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * ??????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2020/1/8 21:02
     */
    @RabbitListener(queues = OrderQueueNameConstant.PAYMENT_NOTIFY)
    public void paymentNotify(WxPayNotifyResultDto message, MessageProperties properties, Channel channel) throws IOException {
        log.info("????????????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());
        try {
            orderService.paymentNotify(Long.parseLong(message.getOutTradeNo()), message.getTenantId());
            log.info("????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            channel.basicAck(properties.getDeliveryTag(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * ??????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2020/1/8 21:02
     */
    @RabbitListener(queues = OrderQueueNameConstant.REFUND_NOTIFY)
    public void refundNotify(RefundNotifyResultDto message, MessageProperties properties, Channel channel) throws IOException {
        log.info("??????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());
        try {
            orderService.refundNotify(message);
            log.info("????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            channel.basicAck(properties.getDeliveryTag(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * ???????????????????????????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2019/12/9 20:20
     */
    @RabbitListener(queues = OrderQueueNameConstant.DELIVER_CREATE)
    public void deliverCreate(GenerateSendBillOrderMessage message, MessageProperties properties, Channel channel) throws IOException {
        log.info("???????????????????????????:????????????{},????????????{}.", DateUtil.now(), message.toString());
        try {
            TenantContextHolder.setTenantId(message.getTenantId());
            if (message.getOrderIds().isEmpty()) {
                log.info("???????????????????????????,????????????{}, ????????????{}", "??????ID????????????", DateUtil.now());
            } else {
                orderDeliveryService.updateShipping(message);
                log.info("???????????????????????????: ????????????{}", DateUtil.now());
            }
            channel.basicAck(properties.getDeliveryTag(), true);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * ????????????????????????????????????
     *
     * @param message
     * @param properties
     * @param channel
     * @return void
     * @author alan
     * @date 2019/12/9 20:21
     */
    @RabbitListener(queues = OrderQueueNameConstant.DELIVER_RECEIPT)
    public void deliverReceipt(ReceiptSendBillOrderMessage message, MessageProperties properties, Channel channel) throws IOException {
        log.info("???????????????????????????:deliveryTag{},????????????{},????????????{}.", properties.getDeliveryTag(), DateUtil.now(),
                message.toString());
        try {
            TenantContextHolder.setTenantId(message.getTenantId());
            if (message.getOrderIds().isEmpty()) {
                log.info("???????????????????????????,????????????{}, ????????????{}", "??????ID????????????", DateUtil.now());
            } else {
                orderDeliveryService.receiptSendBill(message);
                log.info("???????????????????????????: deliveryTag{}", properties.getDeliveryTag());
            }
            channel.basicAck(properties.getDeliveryTag(), true);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}

