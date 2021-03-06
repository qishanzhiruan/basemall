package com.medusa.gruul.logistics.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.additional.update.impl.LambdaUpdateChainWrapper;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.core.util.Result;
import com.medusa.gruul.common.core.util.SystemCode;
import com.medusa.gruul.logistics.api.entity.LogisticsExpress;
import com.medusa.gruul.logistics.api.entity.LogisticsExpressPrint;
import com.medusa.gruul.logistics.mapper.LogisticsExpressMapper;
import com.medusa.gruul.logistics.mapper.LogisticsExpressPrintMapper;
import com.medusa.gruul.logistics.model.dto.manager.LogisticsExpressDto;
import com.medusa.gruul.logistics.model.dto.manager.LogisticsExpressPrintDto;
import com.medusa.gruul.logistics.model.dto.manager.express.ExpressInfoDto;
import com.medusa.gruul.logistics.model.param.LogisticsExpressParam;
import com.medusa.gruul.logistics.model.param.LogisticsExpressPrintParam;
import com.medusa.gruul.logistics.model.vo.LogisticsAddressVo;
import com.medusa.gruul.logistics.model.vo.LogisticsExpressPrintVo;
import com.medusa.gruul.logistics.model.vo.LogisticsExpressVo;
import com.medusa.gruul.logistics.service.ILogisticsExpressPrintService;
import com.medusa.gruul.logistics.service.ILogisticsExpressService;
import com.medusa.gruul.logistics.util.express.kuaidihelp.KuaiDiHelp;
import com.medusa.gruul.logistics.util.express.sf.SFExpressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * @author Administrator
 */
@Service
@Slf4j
public class LogisticsExpressPrintServiceImpl implements ILogisticsExpressPrintService {

    @Autowired
    private LogisticsExpressPrintMapper logisticsExpressPrintMapper;

    /**
     * ????????????????????????????????????
     * @param logisticsExpressPrintParam
     * @return IPage<LogisticsExpressPrintVo>
     */
    @Override
    public IPage<LogisticsExpressPrintVo> getExpressPrintList(LogisticsExpressPrintParam logisticsExpressPrintParam) {
        IPage<LogisticsExpressPrintVo> page = new Page<>(logisticsExpressPrintParam.getCurrent(), logisticsExpressPrintParam.getSize());
        List<LogisticsExpressPrintVo> logisticsExpressPrintVos = this.logisticsExpressPrintMapper.queryAllLogisticsExpressPrintList(page, logisticsExpressPrintParam);
        return page.setRecords(logisticsExpressPrintVos);
    }

    /**
     * ???????????????????????????????????????
     * @param id
     * @return LogisticsExpressPrintVo
     */
    @Override
    public LogisticsExpressPrintVo getExpressPrintInfo(Long id) {
        return this.logisticsExpressPrintMapper.queryLogisticsExpressPrintInfo(id);
    }

    /**
     * ??????/?????? ????????????????????????
     * @param logisticsExpressPrintDto
     */
    @Override
    public void setExpressPrintInfo(LogisticsExpressPrintDto logisticsExpressPrintDto) {
        if (logisticsExpressPrintDto.getId() == null) {
            //??????
            LogisticsExpressPrint logisticsExpressPrint = logisticsExpressPrintDto.coverBean();
            int insert = this.logisticsExpressPrintMapper.insert(logisticsExpressPrint);
            if(insert == 0){
                throw new ServiceException("??????????????????????????????????????????", SystemCode.DATA_ADD_FAILED.getCode());
            }
        } else {
            //??????
            LogisticsExpressPrint logisticsExpressPrintSearch = this.logisticsExpressPrintMapper.selectById(logisticsExpressPrintDto.getId());
            if(BeanUtil.isEmpty(logisticsExpressPrintSearch)){
                throw new ServiceException("???????????????????????????????????????????????????", SystemCode.DATA_EXISTED.getCode());
            }
            LogisticsExpressPrint logisticsExpressPrint = logisticsExpressPrintDto.coverBean();
            int update = this.logisticsExpressPrintMapper.updateById(logisticsExpressPrint);
            if(update == 0){
                throw new ServiceException("??????????????????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
            }
        }
    }

    /**
     * ??????/??????????????????????????????
     * @param type 0-?????? 1-??????
     * @param id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setExpressPrintStatus(Integer type, Long id) {
        boolean sign = new LambdaUpdateChainWrapper<>(logisticsExpressPrintMapper)
                .eq(LogisticsExpressPrint::getId, id)
                .set(LogisticsExpressPrint::getStatus, type).update();
        if (!sign) {
            throw new ServiceException("??????????????????????????????", SystemCode.DATA_UPDATE_FAILED.getCode());
        }
    }

    /**
     * ??????????????????????????????
     * @param id ??????
     */
    @Override
    public void delExpressPrintInfo(Long id) {
        LogisticsExpressPrint logisticsExpressPrint = this.logisticsExpressPrintMapper.selectById(id);
        if(BeanUtil.isEmpty(logisticsExpressPrint)){
            throw new ServiceException("?????????????????????????????????", SystemCode.DATA_EXISTED.getCode());
        }else{
            int delete = this.logisticsExpressPrintMapper.deleteById(id);
            if(delete == 0){
                throw new ServiceException("??????????????????????????????????????????", SystemCode.DATA_DELETE_FAILED.getCode());
            }
        }
    }
}
