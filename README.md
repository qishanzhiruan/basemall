注:该代码为1.0版本 以下介绍为v1.5部分 
### [前言](http://www.bgniao.cn)



| 交流群 |  技术微信 |
|--------|--------|
|![](https://images.gitee.com/uploads/images/2021/0918/112958_165bc860_8533008.png "屏幕截图.png")|![](https://images.gitee.com/uploads/images/2021/0909/104715_7e44acdf_8533008.png "0af656df7854299aa6dd432949d5c47.png")|




启山智软社区团购是一款系统稳定且经过线上反复论证并拥有大量真实用户使用的Java社区团购系统。

基于市场的反馈和变化，我们在不断开发完善社区团购的基础上，还抽离了一套属于我们自己的智慧门店物流配送系统，来帮助线下门店针对货物进行系统下单，批量出单，合理分配，精准配送，在投放各大门店使用后，针对实际情况中出现的各种问题，我们不断的改进，收获了大家的好评和喜欢。

我们由衷希望启山智软社区团购系统可以通过gitee平台让更多的人了解到好的产品。同时欢迎大家积极交流沟通，如有不足之处多给我们的项目提意见或建议，实现共同进步。
![输入图片说明](https://images.gitee.com/uploads/images/2021/0515/171604_0859cc6f_8533008.jpeg "微信截图_20210515164707.jpg")


### 相关链接
	社区团购		: https://www.bgniao.cn
	公司官网		: https://73app.cn
	团购更新文档	: https://www.bgniao.cn/notice
	商家后台		: https://www.bgniao.cn
	区域团长后台	: https://www.bgniao.cn
    商务对接wx/电话	: xcxqidiankeji/18967889883

### 项目介绍

启山智软社区团购是基于Spring Cloud 和 Vue.js的JAVA社区团购系统。包含总控制后台 、城市合伙人(商家pc端)、 区域团长后台 、用户端小程序 、手机H5等多个操作模块。为响应用户需求我们新增了后台自定义装修组件模块，使页面更加美观，操作更加灵活简便。淘宝商品CSV一键导入，提升用户使用感。还有与众不同的管理台侧边栏设计，打破传统管理台样式。 另有公众号接龙、引导页上传、区域团长额外佣金设置、独立提货点、店铺经营概况 、自定义分类专区、直播开通、物流(发货单)等多功能模块。可支持二开，私有化部署，需求功能定制。

### 必读

如需深入了解社区团购系统功能信息，可添加商务负责人 **微信：xcxqidiankeji**  获取测试账号进行体验。

如需深入了解智慧门店物流配送系统功能信息，可添加商务负责人 **微信：xcxqidiankeji** 。

商家需知 : 本平台提供商家资源入驻功能，可根据自身需求来选择对应版本，以最低的成本拥有自己的社区团购小程序。

开发者需知: 启山智软社区团购我们只开放相关文档及项目介绍。如需了解源码相关情况请添加负责人wx咨询，本公司技术人员可提供相关设计文档、数据字典、操作使用教程、接口文档、部署教程文档等资料。

如有公益项目需使用启山智软社区团购我司愿提供免费商家入驻及一切售后服务来奉献力所能及的爱心，公益项目需提供相关证明,我司将拥有针对该项的最终解释权。  

### 项目演示

![](https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/open.png "启山智软社区团购小程序码")
![小程序码](https://images.gitee.com/uploads/images/2021/0602/144623_fac92fca_8533008.jpeg "下载.jpg")

### 荣誉资质
|![输入图片说明](https://images.gitee.com/uploads/images/2021/0908/150857_e5b71af2_8533008.png "微信图片_社区团购资质证书1.png")|![输入图片说明](https://images.gitee.com/uploads/images/2021/0908/151131_413737cf_8533008.png "微信图片_布谷鸟资质证书.png")|![输入图片说明](https://images.gitee.com/uploads/images/2021/0814/104233_f71a9b70_8533008.png "外观专利.png")|
|---|---|---|


### 技术选型

| 技术                   | 说明                 | 官网                                                 |
| ---------------------- | -------------------- | ---------------------------------------------------- |
| Spring Cloud           | 微服务框架           | https://spring.io/projects/spring-cloud              |
| Spring Cloud Alibaba   | 微服务框架           | https://github.com/alibaba/spring-cloud-alibaba      |
| Spring Boot            | 容器+MVC框架         | https://spring.io/projects/spring-boot               |
| MyBatis-Plus           | 数据层代码生成       | http://www.mybatis.org/generator/index.html          |
| Swagger                | 文档生成工具         | https://swagger.io/     
|                                                                                                     |
| Elasticsearch          | 搜索引擎             | https://github.com/elastic/elasticsearch             |
| RabbitMq               | 消息队列             | https://www.rabbitmq.com/                            |
| Redis                  | 分布式缓存           | https://redis.io/                                    |
| Druid                  | 数据库连接池         | https://github.com/alibaba/druid                     |
| OSS                    | 对象存储             | https://github.com/aliyun/aliyun-oss-java-sdk        |
| JWT                    | JWT登录支持          | https://github.com/jwtk/jjwt                         |
| XXL-JOB                | 分布式任务调度平台   |https://www.xuxueli.com/xxl-job/                       |
|                                                                                                     |
| Lombok                 | 简化对象封装工具     | https://github.com/rzwitserloot/lombok               |
| Jenkins                | 自动化部署工具       | https://github.com/jenkinsci/jenkins                 |
| Docker                 | 应用容器引擎         | https://www.docker.com/                              |          
|Sonarqube				 | 代码质量控制	        |https://www.sonarqube.org/
|                                                                                                     |
| element                | 组件库         | https://element.eleme.cn/#/zh-CN                           |
| Vue.js                 | 渐进式JavaScript 框架       | https://cn.vuejs.org/                         |
| Vue-router 			 | 前端路由 		       | https://router.vuejs.org/zh/ 	                            |
| vuex 					 | 状态管理            | https://vuex.vuejs.org/zh/ 								|
| modeuse-core 			 | 自主开发UI组件       | -- 													|
| TypeScript             | JavaScript超集       | https://www.tslang.cn/                    
|                        |
| eslint             	 | 代码质量控制         | https://eslint.org/                                   |                 
| hook	             	 | 代码质量控制         |                                                       |
                 

### 系统架构图
![](https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/sasasa1.png "布谷鸟社区团购架构图")

### 功能模块
	基础服务：
		oss对象存储 支持 阿里云 腾讯云 七牛云(加速图片读取速度)
		sms短信服务 支持 腾讯云 阿里云   
		支付服务    支持微信支付、余额支付，额外对接了盛付通，使商家提现费率更低，自动分账操作更方便。
		总台服务    控制商户入驻及各种信息私有配置
	功能服务：
		商品服务		: 商品展示更加美观，多专区自由切换，操作流程简单 ，支持淘宝商品CSV一键导入等。
		订单服务		: 支持多方式付款，积分，满减，优惠券，会员,一元换购，秒杀，社群接龙等多样化营销工具来增加客户粘合度等。
		直播服务		: 通过小程序直播助手，对商品进行直播，并可获取直播点赞量观看量等，增加获客渠道。
		DIV装修		: 商家可通过自定义装修来根据自己的喜好装修小程序,操作简单，更加灵活。
		数据服务		: 获取商家近期的交易概况(待支付|待发货|待签收|待评论|) 
		 实时概况	: 获取商家指定时间内的实时概况(交易量|交易额|浏览量|佣金)
		 排行榜		: 获取商家当月的排行榜信息数据(团长排行榜|供应商排行版|商品排行榜)



## [更新详细说明](https://www.bgniao.cn/notice) https://www.bgniao.cn/notice

### B/S 社区团购商家端功能清单
经营概况

		实时概况		: 根据时间获取该期间内的商家交易情况实时反馈给商家最直观的感受
		交易概况		: 订单相关数据,包含(待付款,待发货,待签收,待提货)等订单相关状态信息 
		排 行 榜		: 展示当月内团长,供应商,商品的排行信息(交易额 or 交易量)
		... ... 	: 更多内容请添加商务微信获取商家端操作账号进行体验

		
商品管理

		自定义商品专区    : 商品分区展示,实时专区，秒杀，商超，拼团等自定义专区随意切换。 
		产品列表		 : 设置商品投放区域,填充虚拟销量,多规格设置,一键转移专区,购买赠送积分等。
		cvs素材导入	 : 素材导入-在淘宝clone一键导入商品素材,减少商家重复式的工作量。
		... ...		 : 更多内容请添加商务微信获取商家端操作账号进行体验。
订单管理

		快递订单		: 物流发货,商家商品销售不再局限于当前城市
		社区订单		: 社区形式订单，发货单、 分拣单、 签收单，一键导出表格提供给商家最直观的销售数据分析
		订单评价管理     : 用户对已购商品进行评价 打分。商家可进行回复，可将用户评价设置为精选展现至小程序端
		... ...		: 更多内容请添加商务微信获取商家端操作账号进行体验
社群拼团

		拼团活动		: 统一管理所有已创建活动，进行中和即将开始的活动可以进行编辑等操作
		团长管理		: 统一管理所有团长，可以启用/停用团长资格，根据团长表现升级区域团长，也可编辑团长基本信息及删除等操作
		区域团长管理	: 统一管理所有区域团长，可以启用/停用区域团长资格，也可编辑区域团长基本信息及删除等操作       
		... ...		: 更多内容请添加商务微信获取商家端操作账号进行体验
营销应用

	   优惠券		: 商家在店铺后台创建发放优惠券(满减|满折|无门槛|折扣) 增加用户黏性
	   满  减		: 商家在店铺后台创建满减活动，选择指定商品参加活动，用户下单付款该商品可立减优惠
	   积分商城		: 商家推出积分商品、积分优惠券引流，拓客，保持客户粘性，带动其他商品销售量，还可通过授权微信步数兑换积分。
	   直  播		: 商家通过微信直播带货，可接入手机或其他设备进行直播，商家可以根据团长手动控制投放直播间展示
	   社群接龙		: 商家后台创建接龙活动，团长在群里发送链接，用户可点击链接进行购买商品，也可进入小程序参加接龙活动购买商品
	   ... ...		: 一元换购 秒杀等更多功能玩法请进入后台了解 
财务管理

	   对账单		: 商家可实时交易流水记录
	   提现工单		: 统一管理每个角色的提现审核及提现记录
	   ... ...		: 更多内容请添加商务微信微信获取商家端操作账号进行体验
客户管理

	   客户列表		: 统一管理所有用户信息 ，列如充值积分 赠送优惠券，升级为会员等
	   会员管理		: 统一管理会员用户信息， 会员开卡形式(付费|积分|累计消费金额|购买指定商品等)
	   ... ...		: 更多内容请添加商务微信获取商家端操作账号进行体验
配送方式
	  
	   快递配送		: 设置多运费模块(包邮|按件收费|按重量收费) ，开通对应物流服务，查看物流规则等
	   社区配送		: 设置发货单自动生成时间，可拥有独立提货点或团长提货点合并方式，后台查看佣金明细等操作  
	   ... ...		: 更多内容请添加商务微信获取商家端操作账号进行体验
商城设置
		
	   交易设置		: 设置订单交易规则(订单设置|售后设置)，开启或关闭用户下单语音播报提醒等
	   支付设置		: 微信支付 接入(第三方支付)盛付通，减少商家、 提货点、 团长等角色的提现所需手续费，自动分账操作便捷
	   ... ...              : 更多内容请添加商务微信获取商家端操作账号进行体验


## 角色说明
![]( https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/platform.png "角色说明")


## 用户端小程序页面展示
![](https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/e2eb889d26af1165815b4b9019980d5.jpg "布谷鸟社区团购用户端页面")


## 商家端页面展示
	经营概况 产品列表
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611543033(1).jpg" width="600" height="600">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611543676(1).jpg" width="450" height="500">
	
	商品分类 素材导入

<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611543972(1).jpg" width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611543937(1).jpg" width="450" height="500">

	社区订单 签收单
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611990559(1).jpg" width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544273(1).jpg" width="450" height="500">


	活动商品 团长信息
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544500.jpg" width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544500(1).jpg" width="450" height="500">

	积分商城 直播
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544698.jpg" width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544698(1).jpg" width="450" height="500">

	客户列表 会员卡管理
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544903(1).jpg " width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611544922(1).jpg" width="450" height="500">

	快递配送 社区配送
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545171(1).jpg " width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545171.jpg" width="450" height="500">

	引导页 协议设置 交易设置 佣金设置
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545316(1).jpg " width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545330(1).jpg" width="450" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545363(1).jpg " width="500" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545381(1).jpg" width="450" height="500">

	自定义装修
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545742(1).jpg " width="1000" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545782(1).jpg " width="300" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545804.jpg " width="300" height="500">
<img src="https://bgniao-small-file-1253272780.cos.ap-chengdu.myqcloud.com/group_purchase_open/1611545831(1).jpg " width="300" height="500">

