首先感谢大家对Smart Shop支持与耐心等待，Smart Shop商城于9月4日正式开源，小伙伴们赶快来围观吧

近期我们会陆续开放文档及其代码供大家提前熟悉准备


**Smart Shop商城使用前置条件**

1. 营业执照
 2. Linux服务器8核16G
 3. 备案域名一个(小程序上线)
 4. OSS云存储（阿里云、腾讯云等）
 5. SMS短信申请（阿里云、腾讯云等）
 6. 微信小程序申请
 7. 微信商户号申请
 8. 购买快宝盒
 9. 商家支付宝、微信商户证书(包含支付和退款)
10. 中通、顺丰快递客户号申请
11. 微信开放平台第三方平台(第一次认证需花费300元)
12. 微信开放平台网站应用 (pc端调取二维码扫码认证使用)


 
 **微信开放平台网站应用申请** 
      
微信开放平台网址：https://open.weixin.qq.com

1.创建网站应用
![输入图片说明](https://images.gitee.com/uploads/images/2021/0906/101746_1154f9da_8533008.png "屏幕截图.png")

- 注：微信开放平台网站信息登记表需要盖公章，填写要规范
- 回调域名：填写网站要使用的域名即：nginx配置的城市合伙人访问地址
- 网站应用需要获得微信登入权限：(微信登入权限在账号中心认证开发者资质认证)


2.创建第三方平台
|选择第三方平台|选择平台型服务商|创建后授权信息展示样式|
|---|---|---|
|![输入图片说明](https://images.gitee.com/uploads/images/2021/0906/102309_a16af02b_8533008.png "微信截图_20210906102255.png")|![输入图片说明](https://images.gitee.com/uploads/images/2021/0906/102322_07fa96a1_8533008.png "微信截图_20210906102205.png")|![输入图片说明](https://images.gitee.com/uploads/images/2021/0906/102332_89fcf0b5_8533008.png "微信截图_20210906102138.png")|
   
- 注：平台类型为【平台型服务商】
- 选择权限：小程序或公众号可按需求勾选
- 授权发起页域名：部署项目的域名
- 授权测试公众号/小程序列表：小程序原始id
- 授权事件接收URL：域名/api/platform/notify/receive_ticket
- 消息校验Token：superprism（可自定义）
- 消息加解密Key：43位
- 消息与事件接收URL：域名/api/platform/notify/$APPID$/callback
- 小程序服务器域名：填写服务器域名
- 小程序业务域名：业务域名
- 白名单IP地址列表：服务器ip


 **相关应用安装** 
    
- Htop
- nacos           
- nginx
- nexus
- docker
- Harbor
- Mysql
- Redis
- rabbitmq
- jenkins
- JDK
- xxl-job-admin

- Htop(视图化top) 可装可不装 

       yum -y install epel-release
       yum -y install htop
       https://www.cnblogs.com/zangfans/p/8595000.html 

- Nginx web代理
  
        yum install nginx-1.16.1
- Nacos
  
         解压 tar.gz
         移动到bin目录下  cd /nacos/bin 
         单机版启动 ：sh startup.sh -m standalone    
         访问 ：ip:8848/nacos   
                user :nacos
                pwd:nacos
         如需使用数据库进行配置请执行初始化sql及更改配置
         /nacos/conf/nacos-mysql.sql
         /nacos/conf/application.properties.example
        注：nacos密码有进行加密 要自己生成一个
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
            String pwd = new BCryptPasswordEncoder().encode("123456");
![nacos数据库配置](https://images.gitee.com/uploads/images/2021/0909/093854_52bcc674_8055613.png "屏幕截图.png")
         
- Docker

        sudo wget -qO- https://get.docker.com | sh
        版本查看 ： docker version
        启动     ： service docker start
        存储查看 :  docker info
        镜像源修改 ： vim /etc/docker/daemon.json
        docker重启 ：
                    systemctl daemon-reload 守护线程
                    systemctl restart docker 重启docker
        详细使用请大家自行百度。如遇问题可加入qq群,会有专人解答
    
- Harbor docker视图化仓库

        解压zip 
        修改相关配置
        启动  ：    ./install.sh
        查看  ：   docker ps
     
- Mysql 
       
        请大家自行安装 如遇问题可加入qq群,会有专人解答 5.7版本及以上
     
- Redis
       
        请大家自行安装 如遇问题可加入qq群,会有专人解答
        
- rabbitmq 
       
        因使用延迟队列,我们提供专门docker镜像
        docker pull xiaoq123/mq-image:3.8
        docker run -d -p 15672:15672 -p 5672:5672  --name rabbitmq --restart always -e RABBITMQ_DEFAULT_USER=用户名 -e RABBITMQ_DEFAULT_PASS=密码 
        xiaoq123/mq-image:3.8
- xxl-job-admin
       
        因版本变动过大,我们提供专门docker镜像
        docker pull xiaoq123/xxl-job-image:2.1.1
        docker run -e PARAMS="--spring.datasource.url=jdbc:mysql://数据库地址?Unicode=true&characterEncoding=UTF-8 --spring.datasource.username=数据库账号--spring.datasource.password=数据库密码 " -p 9010:8080 -v /tmp:/data/applogs --name xxl-job-admin  --privileged=true -d xiaoq123/xxl-job-image:2.1.1

 
- zip压缩包获取
       
        链接：https://pan.baidu.com/s/1rXzEYl_Korj9T27ptplRbA 
        提取码：64s1 
        --来自百度网盘超级会员V4的分享      
         
        安装过程中出现问题或疑惑可加入qq群探讨
 
***
   
   
    项目启动前 需要安装完成 Nacos Nexus Docker Mysql Redis rabbitmq xxl-job-admin
    
    项目上线前 需要安装完成 Nacos Nexus Docker Mysql Redis rabbitmq xxl-job-admin Jenkins Nginx Harbor JDK 
    如有疑问可加入qq群进行沟通

***


项目介绍 
        
        gruul 
        account-open                    用户服务
        platform-open                   平台服务
        gruul-sms-open                  短信服务
        grull-oss-open                  oss存储服务(内存不足时可优先考虑进行合并)
        gruul-afs-open                  售后服务
        gruul-goods-open                商品服务
        gruul-order-open                订单服务
        gruul-shops-open                店铺服务
        grull-payment-open              支付服务
        gruul-gateway-open              网关服务
        gruul-discount-open             抵扣服务
        gruul-logistics-open            物流服务
        
nginx配置
  
    nginx使用教程请参考 Nginx中文文档 (https://www.nginx.cn/doc/)
    
      server {
        listen 80;
        listen [::]:80;
        server_name 域名;
        return 301 https://域名/$request_uri;
      }
      server{
        listen 443 ssl ;
        listen [::]:443 ssl ;
        server_name 域名;
        #SSL
        ssl_certificate /etc/nginx/ssl/域名证书.crt;
        ssl_certificate_key /etc/nginx/ssl/域名证书.key;
        ssl_session_timeout 5m;
        ssl_protocols TLSv1 TLSv1.1 TLSv1.2;
        ssl_ciphers ALL:!ADH:!EXPORT56:RC4+RSA:+HIGH:+MEDIUM:+LOW:+SSLv2:+EXP;
        ssl_prefer_server_ciphers on;

         #后端接口
        location /api/ {
          if ($request_method = OPTIONS ) {
              add_header Access-Control-Allow-Origin "*";
              add_header Access-Control-Allow-Methods "POST, GET, PUT, OPTIONS, DELETE, HEAD";
              add_header Access-Control-Max-Age "3600";
              add_header Access-Control-Allow-Headers "DNT,X-Mx-ReqToken,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Authorizationi, *";
              add_header Access-Control-Allow-Credentials "true";
              add_header Content-Length 0;
              add_header Content-Type text/plain;
              return 204;
          }

           add_header 'Access-Control-Allow-Origin' "*";
           add_header 'Access-Control-Allow-Methods' 'POST, GET, PUT, OPTIONS, DELETE, HEAD';
           add_header 'Access-Control-Allow-Headers' 'DNT,X-Mx-ReqToken,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Authorization,*';
           add_header 'Access-Control-Allow-Credentials' 'true';
           add_header Access-Control-Max-Age "3600";
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_pass http://127.0.0.1:10999/;
        }
       }



