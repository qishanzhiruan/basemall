package com.medusa.gruul.account.mq;

import com.medusa.gruul.account.api.constant.AccountExchangeConstant;
import com.medusa.gruul.account.api.constant.AccountQueueNameConstant;
import com.medusa.gruul.account.api.enums.AccountQueueEnum;
import com.medusa.gruul.order.api.constant.OrderConstant;
import com.medusa.gruul.order.api.constant.OrderQueueEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;

import javax.annotation.Resource;

/**
 * @Description: RabbitConfig.java
 * @Author: alan
 * @Date: 2019/10/6 14:01
 */
@Slf4j
@Configuration
public class RabbitConfig implements RabbitListenerConfigurer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.setMessageHandlerMethodFactory(messageHandlerMethodFactory());
    }

    @Bean
    MessageHandlerMethodFactory messageHandlerMethodFactory() {
        DefaultMessageHandlerMethodFactory messageHandlerMethodFactory = new DefaultMessageHandlerMethodFactory();
        messageHandlerMethodFactory.setMessageConverter(consumerJackson2MessageConverter());
        return messageHandlerMethodFactory;
    }

    @Bean
    public MappingJackson2MessageConverter consumerJackson2MessageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate() {
        // ??????jackson ???????????????
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setEncoding("UTF-8");
        // ???????????????????????????????????????yml???????????? publisher-returns: true
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            String correlationId = message.getMessageProperties().getCorrelationId();
            log.info("?????????{} ????????????, ????????????{} ?????????{} ?????????: {}  ?????????: {}", correlationId, replyCode, replyText, exchange,
                    routingKey);
        });
        // ???????????????yml???????????? publisher-confirms: true
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("???????????????exchange??????,id: {}", correlationData.getId());
            } else {
                log.info("???????????????exchange??????,??????: {}", cause);
            }
        });
        return rabbitTemplate;
    }

    /**
     * ???????????????????????????
     */
    @Bean
    DirectExchange accountDirect() {
        return (DirectExchange) ExchangeBuilder.directExchange(AccountExchangeConstant.ACCOUNT_EXCHANGE).durable(true)
                .build();
    }

    /**
     * ???????????????????????????
     */
    @Bean
    DirectExchange memberDirect() {
        return (DirectExchange) ExchangeBuilder.directExchange(AccountExchangeConstant.MEMBER_EXCHANGE).durable(true)
                .build();
    }

    /**
     * ????????????????????????
     */
    @Bean
    public Queue accountIntegralQueue() {
        return new Queue(AccountQueueEnum.QUEUE_ACCOUNT_INTEGRAL_CHANGE.getName(), true);
    }


    /**
     * ??????????????????roukey
     */
    @Bean
    Binding integralChangeBinding(DirectExchange accountDirect, Queue accountIntegralQueue) {
        return BindingBuilder.bind(accountIntegralQueue).to(accountDirect)
                .with(AccountQueueEnum.QUEUE_ACCOUNT_INTEGRAL_CHANGE.getRouteKey());
    }


    /**
     * ???????????????????????????????????????????????????
     */
    @Bean
    DirectExchange orderDirect() {
        return (DirectExchange) ExchangeBuilder.directExchange(OrderConstant.EXCHANGE_NAME).durable(true).build();
    }

    /**
     * ??????????????????????????????
     */
    @Bean
    public Queue orderPayOkQueue() {
        return new Queue(AccountQueueNameConstant.ACCOUNT_ORDER_PAY_OK_QUEUE_CHANGE, true);
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderPayBinding(DirectExchange orderDirect, Queue orderPayOkQueue) {
        return BindingBuilder.bind(orderPayOkQueue).to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_PAYED.getRouteKey());
    }

    /**
     * ??????????????????????????????
     */
    @Bean
    public Queue orderCompleteOkQueue() {
        return new Queue(AccountQueueNameConstant.ACCOUNT_ORDER_COMPLETE_OK_QUEUE_CHANGE, true);
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderCompletedBinding(DirectExchange orderDirect, Queue orderCompleteOkQueue) {
        return BindingBuilder.bind(orderCompleteOkQueue).to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_COMPLETED.getRouteKey());
    }

    @Bean
    public Queue accountCollectQueue() {
        return new Queue(AccountQueueNameConstant.ACCOUNT_COLLECT, true);
    }

    /**
     * ??????????????????roukey
     */
    @Bean
    Binding accountCollectBinding(DirectExchange accountDirect, Queue accountIntegralQueue) {
        return BindingBuilder.bind(accountIntegralQueue).to(accountDirect)
                .with(AccountQueueEnum.QUEUE_ACCOUNT_COLLECT.getRouteKey());
    }


    @Bean
    public Queue accountDefaultQueue() {
        return new Queue(AccountQueueNameConstant.ACCOUNT_DEFAULT, true);
    }

    /**
     * ??????????????????roukey
     */
    @Bean
    Binding accountDefaultBinding(DirectExchange accountDirect, Queue accountDefaultQueue) {
        return BindingBuilder.bind(accountDefaultQueue).to(accountDirect)
                .with(AccountQueueEnum.QUEUE_ACCOUNT_DEFAULT.getRouteKey());
    }


    /**
     * ??????????????????????????????
     */
    @Bean
    public Queue memberPayOkQueue() {
        return new Queue(AccountQueueNameConstant.MEMBER_PAY_OK_QUEUE_CHANGE, true);
    }

    /**
     * ???????????????????????????????????????.order.payed
     */
    @Bean
    Binding memberCompletedBinding(DirectExchange memberDirect, Queue memberPayOkQueue) {
        return BindingBuilder.bind(memberPayOkQueue).to(memberDirect)
                .with(AccountQueueEnum.QUEUE_MEMBER_PAY.getRouteKey());
    }

}
