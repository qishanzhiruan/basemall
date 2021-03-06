package com.medusa.gruul.platform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medusa.gruul.account.api.feign.RemoteMiniAccountService;
import com.medusa.gruul.common.core.constant.CommonConstants;
import com.medusa.gruul.common.core.constant.RegexConstants;
import com.medusa.gruul.common.core.constant.TimeConstants;
import com.medusa.gruul.common.core.constant.enums.AuthCodeEnum;
import com.medusa.gruul.common.core.constant.enums.LoginTerminalEnum;
import com.medusa.gruul.common.core.exception.ServiceException;
import com.medusa.gruul.common.core.util.*;
import com.medusa.gruul.common.data.tenant.TenantContextHolder;
import com.medusa.gruul.common.dto.CurMiniUserInfoDto;
import com.medusa.gruul.common.dto.CurPcUserInfoDto;
import com.medusa.gruul.common.dto.CurShopInfoDto;
import com.medusa.gruul.common.dto.CurUserDto;
import com.medusa.gruul.common.redis.RedisManager;
import com.medusa.gruul.platform.api.entity.*;
import com.medusa.gruul.platform.conf.MeConstant;
import com.medusa.gruul.platform.conf.PlatformRedis;
import com.medusa.gruul.platform.conf.WechatOpenProperties;
import com.medusa.gruul.platform.constant.RedisConstant;
import com.medusa.gruul.platform.constant.ScanCodeScenesEnum;
import com.medusa.gruul.platform.mapper.AccountInfoMapper;
import com.medusa.gruul.platform.model.dto.*;
import com.medusa.gruul.platform.model.dto.agent.BatchNoteDto;
import com.medusa.gruul.platform.model.vo.*;
import com.medusa.gruul.platform.model.vo.agent.AgentMerchantVo;
import com.medusa.gruul.platform.service.*;
import com.medusa.gruul.platform.stp.StpAgentUtil;
import com.medusa.gruul.shops.api.entity.ShopsPartner;
import com.medusa.gruul.shops.api.feign.RemoteShopsService;
import lombok.extern.log4j.Log4j2;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.result.WxMpOAuth2AccessToken;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * ?????????????????????????????? ???????????????
 * </p>
 *
 * @author whh
 * @since 2019-09-07
 */
@Service
@Log4j2
public class AccountInfoServiceImpl extends ServiceImpl<AccountInfoMapper, AccountInfo> implements IAccountInfoService {

    @Autowired
    private WxMpService wxMpService;
    @Autowired
    private IMiniInfoService miniInfoService;
    @Autowired
    private ISendCodeService sendCodeService;
    @Autowired
    private IAuthRoleInfoService authRoleInfoService;
    @Autowired
    private IBaseMenuService baseMenuService;
    @Autowired
    private WechatOpenProperties wechatOpenProperties;
    @Autowired
    private IPlatformShopInfoService platformShopInfoService;
    @Autowired
    private RemoteShopsService remoteShopsService;

    @Autowired
    private ISystemConfService systemConfService;


    /**
     * ????????????Vo
     *
     * @param accountInfo
     * @return
     */
    @Deprecated
    private OldAccountInfoVo getAccountInfoVo(AccountInfo accountInfo) {
        OldAccountInfoVo vo = new OldAccountInfoVo();
        BeanUtils.copyProperties(accountInfo, vo);
        Long subjectId = accountInfo.getId();
        if (StrUtil.isNotEmpty(accountInfo.getBindMiniId())) {
            subjectId = accountInfo.getSubjectId();
        }
        //?????????????????????????????????
        MiniInfo miniInfo = miniInfoService.getByUserDefualtMini(subjectId);
        Optional.ofNullable(miniInfo).ifPresent(obj -> {
            OldAccountInfoVo.MeMini mini = new OldAccountInfoVo.MeMini();
            mini.setId(obj.getId());
            mini.setTenantId(obj.getTenantId());
            mini.setMiniName(obj.getMiniName());
            mini.setMiniHeadIcon(obj.getHeadImageUrl());
            mini.setAppId(obj.getAppId());

            //???????????????????????????
            List<AuthRoleInfo> authRoleInfos = authRoleInfoService.getByUserIdAndTenantId(vo.getId(), obj.getTenantId());
            Optional.ofNullable(authRoleInfos).ifPresent(roleInfos -> {
                List<OldAccountInfoVo.MiniRole> miniRoles = roleInfos.stream().map(authRoleInfo -> {
                    OldAccountInfoVo.MiniRole miniRole = new OldAccountInfoVo.MiniRole();
                    miniRole.setRoleId(authRoleInfo.getId());
                    miniRole.setRoleName(authRoleInfo.getRoleName());
                    miniRole.setRoleCode(authRoleInfo.getRoleCode());
                    return miniRole;
                }).collect(Collectors.toList());
                vo.setMiniRoles(miniRoles);
                OldAccountInfoVo.MiniRole role = new OldAccountInfoVo.MiniRole();
                role.setRoleCode(CommonConstants.NUMBER_ZERO.toString());
                //todo ?????????
                //0???????????????
                if (vo.getMiniRoles().contains(role)) {
                    ShopsPartner shopsPartner = remoteShopsService.getByPlatformIdAndTenantId(accountInfo.getId(), mini.getTenantId());
                    if (shopsPartner != null) {
                        mini.setShopId(shopsPartner.getShopId());
                    }
                }
                //1?????????????????????
                role.setRoleCode(CommonConstants.NUMBER_ONE.toString());
                if (vo.getMiniRoles().contains(role)) {

                }
            });

            //????????????????????????????????????,
            List<MenuDto> menuDtos = baseMenuService.getByTenantIdMenu(obj.getTenantId());
            Optional.ofNullable(menuDtos).ifPresent(menuDto -> {
                Map<Long, List<MenuDto>> menuGroup = menuDto.stream().collect(Collectors.groupingBy(MenuDto::getPId));
                List<OldAccountInfoVo.Menu> oneMenu = menuDto.stream().filter(f -> f.getPId().intValue() == CommonConstants.NUMBER_ZERO).map(menu -> {
                    OldAccountInfoVo.Menu miniMenu = new OldAccountInfoVo.Menu();
                    miniMenu.setTitle(menu.getTitle());
                    miniMenu.setPath(menu.getPath());
                    miniMenu.setName(menu.getName());
                    miniMenu.setIcon(menu.getIcon());
                    miniMenu.setMenuId(menu.getMenuId());
                    miniMenu.setPId(0L);
                    return miniMenu;
                }).collect(Collectors.toList());
                for (OldAccountInfoVo.Menu menu : oneMenu) {
                    List<MenuDto> subDto = menuGroup.get(menu.getMenuId());
                    if (Optional.ofNullable(subDto).isPresent()) {
                        List<OldAccountInfoVo.Menu> subMenus = subDto.stream().map(subDtoMenu -> {
                            OldAccountInfoVo.Menu subMenu = new OldAccountInfoVo.Menu();
                            subMenu.setTitle(subDtoMenu.getTitle());
                            subMenu.setPath(subDtoMenu.getPath());
                            subMenu.setName(subDtoMenu.getName());
                            subMenu.setIcon(subDtoMenu.getIcon());
                            subMenu.setMenuId(subMenu.getMenuId());
                            subMenu.setPId(menu.getMenuId());
                            return subMenu;
                        }).collect(Collectors.toList());
                        menu.setSubMenu(subMenus);
                    }

                }
                vo.setMiniMenus(oneMenu);
            });
            vo.setMiniInfos(mini);
        });
        return vo;
    }


    @Override
    public void checkoutAccount(String phone, Integer type) {
        AccountInfo accountInfo = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("phone", phone));
        //??????????????????
        if (type.equals(CommonConstants.NUMBER_ONE)) {
            if (accountInfo == null) {
                throw new ServiceException("???????????????", SystemCode.DATA_NOT_EXIST.getCode());
            }
            return;
        }
        //?????????????????????
        if (type.equals(CommonConstants.NUMBER_TWO)) {
            if (accountInfo != null) {
                throw new ServiceException("???????????????");
            }
            return;
        }
        throw new ServiceException("????????????");

    }

    @Override
    public OldAccountInfoVo login(TenementLoginDto tenementLoginDto) {
        String key = SecureUtil.md5(tenementLoginDto.getUsername().concat("^2!2$3.").concat(tenementLoginDto.getPassword()));
        PlatformRedis platformRedis = new PlatformRedis();
        String loginKey = RedisConstant.LOGIN_KEY.concat(key);
        String keyData = platformRedis.get(loginKey);
        if (StrUtil.isNotEmpty(keyData)) {
            return JSON.parseObject(keyData, OldAccountInfoVo.class);
        }
        AccountInfo accountInfo = this.getByPhone(tenementLoginDto.getUsername());
        if (accountInfo == null) {
            throw new ServiceException("???????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        String passwd = SecureUtil.md5(tenementLoginDto.getPassword().concat(accountInfo.getSalt()));
        if (!StrUtil.equals(passwd, accountInfo.getPasswd())) {
            throw new ServiceException("????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        updateAccountLastLoignTime(accountInfo.getId());
        OldAccountInfoVo vo = getAccountInfoVo(accountInfo);
        //??????????????????Token redisKey
        String userToken = cachePlatformCurUserDtoOld(accountInfo, vo);
        vo.setToken(userToken);
        long between = getTodayEndTime();
        //????????????redisKey
        platformRedis.setNxPx(loginKey, JSON.toJSONString(vo), between);
        return vo;
    }

    /**
     * ??????????????????????????????
     * ??????12?????????
     *
     * @param info ??????????????????
     * @param vo   ????????????????????????
     * @return java.lang.String   redisKey
     */
    @Deprecated
    private String cachePlatformCurUserDtoOld(AccountInfo info, OldAccountInfoVo vo) {
        CurUserDto curUserDto = new CurUserDto();
        curUserDto.setUserId(info.getId().toString());
        curUserDto.setUserType(1);
        curUserDto.setAvatarUrl(info.getAvatarUrl());
        curUserDto.setGender(info.getGender());
        curUserDto.setOpenId(info.getOpenId());
        curUserDto.setNikeName(info.getNikeName());
        if (vo.getMiniInfos() != null) {
            curUserDto.setShopId(vo.getMiniInfos().getShopId());
        }
        if (CollectionUtil.isNotEmpty(vo.getMiniRoles())) {
            List<CurUserDto.MiniRole> roles = vo.getMiniRoles().stream().map(obj -> {
                CurUserDto.MiniRole miniRole = new CurUserDto.MiniRole();
                BeanUtils.copyProperties(obj, miniRole);
                return miniRole;
            }).collect(Collectors.toList());
            curUserDto.setMiniRoles(roles);
        }
        //?????????????????????????????????????????????????????????
        if (StrUtil.isEmpty(info.getPhone())) {
            return "no";
        }
        PlatformRedis platformRedis = new PlatformRedis();
        long between = getTodayEndTime();
        String tokenValue = SecureUtil.md5(info.getPhone()).concat(info.getSalt()).concat(info.getPasswd());
        String redisKey = RedisConstant.TOKEN_KEY.concat(tokenValue);
        platformRedis.setNxPx(redisKey, JSON.toJSONString(curUserDto), between);

        CurPcUserInfoDto curPcUserInfoDto = new CurPcUserInfoDto();
        curPcUserInfoDto.setUserId(info.getId().toString());
        curPcUserInfoDto.setTerminalType(LoginTerminalEnum.PC);
        curPcUserInfoDto.setAvatarUrl(info.getAvatarUrl());
        curPcUserInfoDto.setGender(info.getGender());
        curPcUserInfoDto.setOpenId(info.getOpenId());
        curPcUserInfoDto.setNikeName(info.getNikeName());
        curPcUserInfoDto.setIsAgent(Boolean.FALSE);
        if (info.getMeAgentId() != null && info.getMeAgentId() > 0) {
            curPcUserInfoDto.setIsAgent(Boolean.TRUE);
        }
        PlatformRedis allRedis = new PlatformRedis(CommonConstants.SHOP_INFO_REDIS_KEY);
        allRedis.setNxPx(tokenValue, JSON.toJSONString(curPcUserInfoDto), between);
        return platformRedis.getBaseKey().concat(":").concat(redisKey);
    }

    /**
     * ??????????????????????????????
     * ??????12?????????
     *
     * @param info ??????????????????
     * @param vo   ????????????????????????
     * @return java.lang.String   redisKey token
     */
    private String cachePlatformCurUserDto(AccountInfo info, AccountInfoVo vo) {
        CurUserDto curUserDto = new CurUserDto();
        curUserDto.setUserId(info.getId().toString());
        curUserDto.setUserType(1);
        curUserDto.setAvatarUrl(info.getAvatarUrl());
        curUserDto.setGender(info.getGender());
        curUserDto.setOpenId(info.getOpenId());
        curUserDto.setNikeName(info.getNikeName());
        LoginShopInfoVo shopInfoVo = vo.getShopInfoVo();
        if (shopInfoVo != null && StrUtil.isNotEmpty(shopInfoVo.getShopId())) {
            curUserDto.setShopId(shopInfoVo.getShopId());
        }
        PlatformRedis platformRedis = new PlatformRedis();
        long between = getTodayEndTime();
        String jwtToken = new JwtUtils(MeConstant.JWT_PRIVATE_KEY).createJwtToken(MeConstant.PLATFORM);
        String redisKey = RedisConstant.TOKEN_KEY.concat(jwtToken);
        platformRedis.setNxPx(redisKey, JSON.toJSONString(curUserDto), between);

        //??????
        CurPcUserInfoDto curPcUserInfoDto = new CurPcUserInfoDto();
        curPcUserInfoDto.setUserId(info.getId().toString());
        curPcUserInfoDto.setTerminalType(LoginTerminalEnum.PC);
        curPcUserInfoDto.setAvatarUrl(info.getAvatarUrl());
        curPcUserInfoDto.setGender(info.getGender());
        curPcUserInfoDto.setOpenId(info.getOpenId());
        curPcUserInfoDto.setNikeName(info.getNikeName());
        PlatformRedis allRedis = new PlatformRedis(CommonConstants.PC_INFO_REDIS_KEY);
        allRedis.setNxPx(jwtToken, JSON.toJSONString(curPcUserInfoDto), between);

        return platformRedis.getBaseKey().concat(":").concat(redisKey);

    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @return 1234ms
     */
    private long getTodayEndTime() {
        Date date = new Date();
        DateTime endOfDay = DateUtil.endOfDay(date);
        return DateUtil.between(date, endOfDay, DateUnit.MS);
    }

    /**
     * ??????????????????????????????
     *
     * @param accountInfoId com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private void updateAccountLastLoignTime(Long accountInfoId) {
        CompletableFuture.runAsync(() -> {
            AccountInfo info = new AccountInfo();
            info.setLastLoginTime(LocalDateTime.now());
            info.setId(accountInfoId);
            this.updateById(info);
        });
    }


    @Override
    public String preAccountScanCode(PreAccountVerifyDto preAccountVerifyDto) {
        if (!ScanCodeScenesEnum.findScenes(preAccountVerifyDto.getScenes())) {
            throw new ServiceException("??????????????????");
        }
        //????????????id?????????1????????????????????????,????????????????????????
        CurUserDto httpCurUser = CurUserUtil.getHttpCurUser();
        if (httpCurUser != null) {
            preAccountVerifyDto.setUserId(Long.valueOf(httpCurUser.getUserId()));
        }
        if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SHOP_INFO_CHECK.getScenes())) {
            Long shopInfoId = preAccountVerifyDto.getShopInfoId();
            if (shopInfoId == null) {
                throw new ServiceException("shopInfoId????????????");
            }
            PlatformShopInfo platformShopInfo = platformShopInfoService.getById(shopInfoId);
            if (platformShopInfo == null) {
                throw new ServiceException("???????????????");
            }
        }

        String redirectUrl = wechatOpenProperties.getDomain().concat("/account-info/account/verify/notify");
        //???????????????????????????snsapi_login???
        String scope = "snsapi_login";
        String state = SecureUtil.md5(System.currentTimeMillis() + "");
        new PlatformRedis().setNxPx(state, JSONObject.toJSONString(preAccountVerifyDto), TimeConstants.TEN_MINUTES);

        return wxMpService.switchoverTo(preAccountVerifyDto.getAppId()).buildQrConnectUrl(redirectUrl, scope, state);
    }

    @Override
    public void accountScanCodeNotify(String code, String state, HttpServletResponse response) {
        if (StrUtil.isEmpty(state)) {
            throw new ServiceException("????????????");
        }
        PlatformRedis platformRedis = new PlatformRedis();
        String jsonData = platformRedis.get(state);
        if (StrUtil.isEmpty(jsonData)) {
            throw new ServiceException("??????????????????????????????");
        }
        PreAccountVerifyDto preAccountVerifyDto = JSONObject.parseObject(jsonData, PreAccountVerifyDto.class);
        Result result = Result.failed();
        //????????????
        if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SWITCHING.getScenes())) {
            result = this.changeTie(preAccountVerifyDto.getAppId(), code, preAccountVerifyDto.getUserId());
            //????????????
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_REGISTER.getScenes())) {
            result = this.createTempAccount(preAccountVerifyDto.getAppId(), code);
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_LOGGIN.getScenes())) {
            result = this.scanCodeLogin(preAccountVerifyDto.getAppId(), code);
        } else if (preAccountVerifyDto.getScenes().equals(ScanCodeScenesEnum.ACCOUNT_SHOP_INFO_CHECK.getScenes())) {
            result = this.verifyShopAccount(preAccountVerifyDto.getAppId(), code, preAccountVerifyDto.getShopInfoId());
        } else {
            throw new ServiceException("????????????");
        }

        //??????????????????,?????????????????????
        StringBuilder redirectUrl = new StringBuilder(preAccountVerifyDto.getRedirectUrl());
        //?????????????????????????????????
        if (preAccountVerifyDto.getRedirectUrl().contains(MeConstant.WENHAO)) {
            redirectUrl.append("&");
        } else {
            redirectUrl.append("?");
        }
        code = SecureUtil.md5(System.currentTimeMillis() + "");
        redirectUrl.append("code=").append(code);
        //???????????????????????????shopInfoId??????????????????????????????????????????????????????
        if (preAccountVerifyDto.getShopInfoId() != null) {
            redirectUrl.append("&shopInfoId=").append(preAccountVerifyDto.getShopInfoId());
        }
        //????????????????????????????????????5?????????????????????
        platformRedis.setNxPx(code.concat(":inside"), JSONObject.toJSONString(result), TimeConstants.TEN_MINUTES);
        //??????????????????,??????????????????,???????????????
        platformRedis.setNxPx(code, JSONObject.toJSONString(result), TimeConstants.TEN_MINUTES);

        try {
            response.sendRedirect(redirectUrl.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param appId      ????????????appId
     * @param code       ??????code
     * @param shopInfoId ??????id
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result verifyShopAccount(String appId, String code, Long shopInfoId) {
        try {
            wxMpService.switchoverTo(appId);
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
            }
            if (accountInfo == null) {
                accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
                if (accountInfo == null) {
                    return Result.failed("?????????????????????");
                }
            }
            PlatformShopInfo platformShopInfo = platformShopInfoService.getById(shopInfoId);
            if (platformShopInfo == null) {
                return Result.failed("?????????????????????,????????????");
            }
            if (!platformShopInfo.getAccountId().equals(accountInfo.getId())) {
                return Result.failed("?????????????????????????????????????????????");
            }
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }
    }

    /**
     * ??????????????????
     *
     * @param appId ????????????appId
     * @param code  ??????code
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result scanCodeLogin(String appId, String code) {

        try {
            wxMpService.switchoverTo(appId);
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
                if (accountInfo != null) {
                    return Result.ok(accountInfo);
                }
            }
            accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
            //openId??????????????????????????????????????????,??????????????????,?????????????????????
            if (accountInfo == null) {
                accountInfo = new AccountInfo();
                getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);
            }
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }
    }

    /**
     * ????????????????????????,?????????????????????,????????????????????????????????????
     *
     * @param appId ???????????????appid
     * @param code  ???????????????code
     * @return com.medusa.gruul.common.core.util.Result
     */
    private Result<AccountInfo> createTempAccount(String appId, String code) {
        this.wxMpService.switchoverTo(appId);
        try {
            AccountInfo accountInfo = null;
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.oauth2getAccessToken(code);
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                accountInfo = this.getByAccountUnionId(wxMpOauth2AccessToken.getUnionId());
                if (accountInfo != null) {
                    return Result.failed("????????????????????????????????????");
                }
            }
            accountInfo = this.getByAccountOpenId(wxMpOauth2AccessToken.getOpenId());
            if (accountInfo != null) {
                return Result.failed("????????????????????????????????????");
            }
            accountInfo = new AccountInfo();
            getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);
            return Result.ok(accountInfo);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getError().getErrorMsg());
        }

    }

    /**
     * ??????openId ??????????????????
     *
     * @param openId ??????openId
     * @return com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private AccountInfo getByAccountOpenId(String openId) {
        return this.getBaseMapper().selectOne(new QueryWrapper<AccountInfo>().eq("open_id", openId));
    }

    /**
     * ??????unionId ??????????????????
     *
     * @param unionId ????????????unionId
     * @return com.medusa.gruul.platform.api.entity.AccountInfo
     */
    private AccountInfo getByAccountUnionId(String unionId) {
        return this.getBaseMapper().selectOne(new QueryWrapper<AccountInfo>().eq("union_id", unionId));
    }

    @Override
    public LoginAccountInfoDetailVo info() {
        CurPcUserInfoDto curUser = CurUserUtil.getPcRqeustAccountInfo();
        if (curUser == null) {
            throw new ServiceException("????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        AccountInfo accountInfo = this.getById(curUser.getUserId());
        AccountInfoVo loginInfoVo = getLoginInfoVo(accountInfo);
        LoginAccountInfoDetailVo vo = new LoginAccountInfoDetailVo();
        BeanUtils.copyProperties(loginInfoVo, vo);
        vo.setBalance(accountInfo.getBalance());
        vo.setAccountType(accountInfo.getAccountType());
        vo.setPhone(accountInfo.getPhone());
        return vo;
    }


    @Override
    public Result<AccountInfo> changeTie(String appId, String code, Long userId) {
        AccountInfo accountInfo = null;
        try {
            WxMpOAuth2AccessToken wxMpOauth2AccessToken = wxMpService.switchoverTo(appId).oauth2getAccessToken(code);
            //?????????????????????????????????
            AccountInfo old = null;
            if (StrUtil.isNotEmpty(wxMpOauth2AccessToken.getUnionId())) {
                old = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("union_id", wxMpOauth2AccessToken.getUnionId()).notIn("id", userId));
            }
            if (old == null) {
                old = this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("open_id", wxMpOauth2AccessToken.getUnionId()).notIn("id", userId));
            }
            if (old != null) {
                throw new ServiceException("???????????????????????????");
            }
            accountInfo = this.baseMapper.selectById(userId);
            AccountInfo info = getMpInfo(accountInfo, wxMpOauth2AccessToken, appId);
            cachePlatformCurUserDtoOld(info, getAccountInfoVo(info));
            this.updateById(info);
        } catch (WxErrorException e) {
            e.printStackTrace();
            return Result.failed(e.getMessage());
        } catch (ServiceException e) {
            return Result.failed(e.getMessage());
        }
        return Result.ok(accountInfo);
    }


    @Override
    public void phoneChangeTie(PhoneChangeTieDto phoneChangeTieDto) {
        sendCodeService.certificateCheck(phoneChangeTieDto.getOneCertificate(), phoneChangeTieDto.getOldPhone(), AuthCodeEnum.ACCOUNT_PHONE_IN_TIE.getType());
        AccountInfo phoneAccount = this.getByPhone(phoneChangeTieDto.getNewPhone());
        if (phoneAccount != null) {
            throw new ServiceException("??????????????????????????????");
        }
        sendCodeService.certificateCheck(phoneChangeTieDto.getTwoCertificate(), phoneChangeTieDto.getNewPhone(), AuthCodeEnum.ACCOUNT_PHONE_IN_TIE.getType());
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (accountInfo == null) {
            throw new ServiceException("??????token");
        }
        accountInfo.setId(accountInfo.getId());
        accountInfo.setPhone(phoneChangeTieDto.getNewPhone());
        this.baseMapper.updateById(accountInfo);
        removeAccountLogin(accountInfo);

    }

    @Override
    public void passChangeTie(PassChangeTieDto passChangeTieDto) {
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (accountInfo == null) {
            throw new ServiceException("????????????");
        }
        if (!accountInfo.getPhone().equals(passChangeTieDto.getPhone())) {
            throw new ServiceException("??????????????????");
        }
        sendCodeService.certificateCheck(passChangeTieDto.getCertificate(), accountInfo.getPhone(), AuthCodeEnum.ACCOUNT_PASSWORD_IN_TIE.getType());
        removeAccountLogin(accountInfo);
        accountInfo.setPassword(passChangeTieDto.getPasswd());
        String salt = RandomUtil.randomString(6);
        accountInfo.setSalt(salt);
        accountInfo.setPasswd(SecureUtil.md5(accountInfo.getPassword().concat(salt)));
        this.baseMapper.updateById(accountInfo);


    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccountInfoVo accountRegister(AccountRegisterDto accountRegisterDto) {

        //??????state??????????????????
        String jsonData = new PlatformRedis().get(accountRegisterDto.getCode().concat(":inside"));
        if (StrUtil.isEmpty(jsonData)) {
            throw new ServiceException("???????????????????????????");
        }
        Result result = JSONObject.parseObject(jsonData, Result.class);
        if (result.getCode() != CommonConstants.SUCCESS) {
            throw new ServiceException("????????????:" + result.getMsg());
        }

        if (this.getByPhone(accountRegisterDto.getPhone()) != null) {
            throw new ServiceException("??????????????????????????????");
        }
        //????????????????????????????????????
        sendCodeService.certificateCheck(accountRegisterDto.getCertificate(), accountRegisterDto.getPhone(), AuthCodeEnum.CREATE_MINI_REGISTER.getType());
        AccountInfo accountInfo = ((JSONObject) result.getData()).toJavaObject(AccountInfo.class);
        accountInfo.setPhone(accountRegisterDto.getPhone());
        accountInfo.setPassword(accountRegisterDto.getPassword());
        accountInfo.setForbidStatus(CommonConstants.NUMBER_ZERO);
        String salt = RandomUtil.randomString(6);
        accountInfo.setSalt(salt);
        accountInfo.setPasswd(SecureUtil.md5(accountRegisterDto.getPassword().concat(salt)));
        accountInfo.setRegion(accountRegisterDto.getRegion());
        accountInfo.setAddress(accountRegisterDto.getAddress());
        accountInfo.setAccountType(CommonConstants.NUMBER_ZERO);
        //Todo ????????? ??????
        this.save(accountInfo);

        CompletableFuture.runAsync(() -> {
            //????????????????????????
            JSONObject json = new JSONObject();
            json.put("first", "??????????????????????????????");
            List<String> keywords = CollectionUtil.newLinkedList(LocalDateTimeUtil.format(accountInfo.getCreateTime(), "yyyy-MM-dd HH:mm:ss"),
                    accountInfo.getNikeName(), accountInfo.getPhone(), accountInfo.getAddress());
            json.put("keyword", keywords);
            json.put("remark", "???????????????????????????");
            systemConfService.sendKfmsg(CommonConstants.NUMBER_ONE, json);
        });

        return getLoginInfoVo(accountInfo);
    }

    @Override
    public AccountInfoVo login(LoginDto loginDto) {
        AccountInfoVo vo = null;
        switch (loginDto.getLoginType()) {
            case 1:
                vo = passwdLogin(loginDto.getPhone(), loginDto.getPassword());
                break;
            case 2:
                vo = phoneCodeLogin(loginDto.getPhone(), loginDto.getCertificate());
                break;
            case 3:
                vo = wxScanCodeLogin(loginDto.getCode());
                break;
            default:
                throw new ServiceException("??????????????????");
        }
        updateAccountLastLoignTime(vo.getId());
        return vo;
    }

    /**
     * @param code code
     * @return
     */
    @Override
    public Result verifyStateResult(String code) {
        PlatformRedis platformRedis = new PlatformRedis();
        String jsonData = platformRedis.get(code);
        if (StrUtil.isEmpty(jsonData)) {
            return Result.failed("code?????????");
        }
        platformRedis.del(code);
        return JSONObject.parseObject(jsonData, Result.class);
    }

    @Override
    public void passwordRetrieve(PasswordRetrieveDto passwordRetrieveDto) {
        AccountInfo accountInfo = getByPhone(passwordRetrieveDto.getPhone());
        if (accountInfo == null) {
            throw new ServiceException("??????????????????");
        }
        removeAccountLogin(accountInfo);
        //????????????????????????????????????
        sendCodeService.certificateCheck(passwordRetrieveDto.getCertificate(), passwordRetrieveDto.getPhone(), AuthCodeEnum.ACCOUNT_FORGET_PASSWD.getType());

        accountInfo.setPassword(passwordRetrieveDto.getPasswd());
        String salt = RandomUtil.randomString(6);
        accountInfo.setSalt(salt);
        accountInfo.setPasswd(SecureUtil.md5(accountInfo.getPassword().concat(salt)));
        this.baseMapper.updateById(accountInfo);


    }

    /**
     * ????????????????????????token
     *
     * @param accountInfo ????????????
     */
    private void removeAccountLogin(AccountInfo accountInfo) {
        PlatformRedis platformRedis = new PlatformRedis();
        String key = SecureUtil.md5(accountInfo.getPhone()).concat(accountInfo.getSalt()).concat(accountInfo.getPasswd());
        String redisKey = RedisConstant.TOKEN_KEY.concat(key);
        platformRedis.del(redisKey);
    }

    /**
     * @param state
     * @return
     */
    private AccountInfoVo wxScanCodeLogin(String state) {
        String jsonData = new PlatformRedis().get(state);
        if (StrUtil.isEmpty(jsonData)) {
            throw new ServiceException("??????????????????");
        }
        Result result = JSONObject.parseObject(jsonData, Result.class);
        if (result.getCode() != CommonConstants.SUCCESS) {
            throw new ServiceException(result.getMsg());
        }
        AccountInfo accountInfo = ((JSONObject) result.getData()).toJavaObject(AccountInfo.class);
        if (accountInfo.getId() == null) {
            throw new ServiceException("????????????????????????");
        }
        return getLoginInfoVo(accountInfo);
    }

    private AccountInfoVo phoneCodeLogin(String phone, String certificate) {
        AccountInfo accountInfo = this.getByPhone(phone);
        if (accountInfo == null) {
            throw new ServiceException("???????????????");
        }
        sendCodeService.certificateCheck(certificate, phone, AuthCodeEnum.MINI_LOGIN.getType());
        return getLoginInfoVo(accountInfo);
    }

    /**
     * ???????????????
     *
     * @param phone    ?????????
     * @param password ??????
     * @return
     */
    private AccountInfoVo passwdLogin(String phone, String password) {
        AccountInfo accountInfo = this.getByPhone(phone);
        if (accountInfo == null) {
            throw new ServiceException("?????????????????????");
        }
        String md5Pw = SecureUtil.md5(password.concat(accountInfo.getSalt()));
        if (!md5Pw.equals(accountInfo.getPasswd())) {
            throw new ServiceException("?????????????????????");
        }
        return getLoginInfoVo(accountInfo);
    }

    /**
     * ??????????????????????????????
     *
     * @param accountInfo ????????????
     * @return com.medusa.gruul.platform.model.vo.AccountInfoVo
     */
    @Override
    public AccountInfoVo getLoginInfoVo(AccountInfo accountInfo) {
        if (accountInfo.getForbidStatus().equals(CommonConstants.NUMBER_ONE)) {
            throw new ServiceException("??????????????????????????????????????????");
        }
        AccountInfoVo vo = new AccountInfoVo();
        BeanUtils.copyProperties(accountInfo, vo);
        //?????????????????????????????????
        PlatformShopInfo shopInfo = platformShopInfoService.getById(accountInfo.getLastLoginShopId());
        if (shopInfo != null) {
            LoginShopInfoVo infoVo = platformShopInfoService.getLoginShopInfoVo(shopInfo);
            this.getShopAccountRoleInfo(accountInfo.getId(), infoVo);

            vo.setShopInfoVo(infoVo);
        }
        vo.setIsAgent(Boolean.FALSE);
        if (accountInfo.getMeAgentId() != null && accountInfo.getMeAgentId() > 0) {
            vo.setIsAgent(Boolean.TRUE);
        }
        //??????????????????Token redisKey
        String userToken = cachePlatformCurUserDto(accountInfo, vo);
        vo.setToken(userToken);
        return vo;
    }

    @Override
    public void getShopAccountRoleInfo(Long accountId, LoginShopInfoVo infoVo) {
//        List<AuthUserRole> userRoles = authUserRoleService.getByUserIdAndTenantId(accountId, infoVo.getTenantId());
//        if (CollectionUtil.isNotEmpty(userRoles)) {
//            List<Long> roleIds = userRoles.stream().map(AuthUserRole::getRoleId).collect(Collectors.toList());
//            List<AuthRoleInfo> roleInfos = authRoleInfoService.getByRoleIds(roleIds);
//            List<RoleInfoVo> roleInfo = roleInfos.stream().map(obj -> BeanUtil.toBean(obj, RoleInfoVo.class)).collect(Collectors.toList());
//            infoVo.setRoleInfoVo(roleInfo);
//        }
    }


    @Override
    public Boolean affirmLessee(String token) {
        String tenantId = TenantContextHolder.getTenantId();
        if (StrUtil.isEmpty(tenantId)) {
            return Boolean.FALSE;
        }
        RedisManager redisManager = RedisManager.getInstance();
        String user = redisManager.get(token);
        if (StrUtil.isEmpty(user)) {
            return Boolean.FALSE;
        }
        CurUserDto curUserDto = JSON.parseObject(user, CurUserDto.class);
        PlatformShopInfo platformShopInfo = platformShopInfoService.getByTenantId(tenantId);
        if (!curUserDto.getUserId().equals(platformShopInfo.getAccountId().toString())) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Override
    public void emailChange(EmailChangeDto emailChangeDto) {
        AccountInfo accountInfo = this.getById(CurUserUtil.getPcRqeustAccountInfo().getUserId());
        if (ObjectUtil.isNull(accountInfo)) {
            throw new ServiceException("????????????");
        }
        AccountInfo up = new AccountInfo();
        up.setId(accountInfo.getId());
        up.setEmail(emailChangeDto.getEmail());
        this.updateById(up);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchNote(BatchNoteDto noteDto) {
        List<AccountInfo> accountInfos = this.listByIds(noteDto.getAccountIds());
        if (CollectionUtil.isEmpty(accountInfos) || accountInfos.size() != noteDto.getAccountIds().size()) {
            throw new ServiceException("?????????????????????");
        }
        for (AccountInfo accountInfo : accountInfos) {
            AccountInfo up = null;
            if (StrUtil.isEmpty(accountInfo.getCommentText())) {
                up = new AccountInfo();
                up.setCommentText(noteDto.getCommentText());
                up.setId(accountInfo.getId());

            } else if (noteDto.getIsCoverage().equals(CommonConstants.NUMBER_ONE)) {
                up = new AccountInfo();
                up.setCommentText(noteDto.getCommentText());
                up.setId(accountInfo.getId());
            }
            if (up != null) {
                this.updateById(up);
            }
        }
    }



    @Override
    public List<AccountInfo> getByAgentId(long agentId) {
        return this.list(new QueryWrapper<AccountInfo>().eq("agent_id", agentId));
    }



    @Override
    public Boolean verifyData(VerifyDataDto verifyDataDto) {
        CurUserDto curUser = CurUserUtil.getHttpCurUser();
        if (curUser == null) {
            throw new ServiceException("????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        AccountInfo accountInfo = this.getById(curUser.getUserId());
        Boolean flag = Boolean.FALSE;
        if (verifyDataDto.getOption().equals(CommonConstants.NUMBER_ONE)) {
            flag = accountInfo.getPhone().equals(verifyDataDto.getPhone());
        }
        return flag;
    }

    private AccountInfo getMpInfo(AccountInfo accountInfo, WxMpOAuth2AccessToken wxMpOauth2AccessToken, String appId) throws WxErrorException {
        WxMpUser wxMpUser = wxMpService.switchoverTo(appId).oauth2getUserInfo(wxMpOauth2AccessToken, "zh_CN");
        accountInfo.setRefreshToken(wxMpOauth2AccessToken.getRefreshToken());
        accountInfo.setAccessToken(wxMpOauth2AccessToken.getAccessToken());
        accountInfo.setAccessExpiresTime(DateUtils.timestampCoverLocalDateTime(wxMpOauth2AccessToken.getExpiresIn()));
        accountInfo.setOpenId(wxMpOauth2AccessToken.getOpenId());
        accountInfo.setCity(wxMpUser.getCity());
        accountInfo.setLanguage(wxMpUser.getLanguage());
        accountInfo.setNikeName(wxMpUser.getNickname());
        accountInfo.setAvatarUrl(wxMpUser.getHeadImgUrl());
        accountInfo.setGender(wxMpUser.getSex());
        accountInfo.setUnionId(StrUtil.isNotEmpty(wxMpUser.getUnionId()) ? wxMpUser.getUnionId() : null);
        accountInfo.setProvince(wxMpUser.getProvince());
        accountInfo.setCountry(wxMpUser.getCountry());
        accountInfo.setPrivilege(JSON.toJSONString(wxMpUser.getPrivileges()));
        return accountInfo;
    }

    private AccountInfo getByPhone(String username) {
        if (!ReUtil.isMatch(RegexConstants.REGEX_MOBILE_EXACT, username)) {
            throw new ServiceException("???????????????", SystemCode.DATA_NOT_EXIST.getCode());
        }
        return this.baseMapper.selectOne(new QueryWrapper<AccountInfo>().eq("phone", username));
    }
}
