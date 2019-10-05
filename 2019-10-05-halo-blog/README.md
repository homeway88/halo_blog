# Halo博客建站小记

## 0. 背景
19年国庆期间宅家里，闲着无聊，心血来潮想搭个博客，想想之前也搞过好几个博客，就是没有坚持下来，很是可惜，一转眼已经工作3年有余，是该把自己的学习过程，整理整理，形成自己的知识沉淀，同时梳理一下自己的技术体系框架，看看是否能形成比较系统的知识。

## 1. 博客系统选择
说干就干，第一步就是选择一个开源的博客系统了。简单搜索一下比较受欢迎的开源博客系统：
* [WordPress](https://wordpress.org) 之类的排除掉，之前接触过，给我的感觉就是太拥肿，并且自己不懂`PHP`, 维护起来比较麻烦。
* [Octopress](https://github.com/octopress/octopress)  静态类博客系统，可以将整个博客内容生成静态的网页，并且可以免费托管在`github`上，但是此类博客维护起来太麻烦，每次修改都要重新生成并上传。
* [Halo](https://github.com/halo-dev/halo) 一款现代化的个人独立博客系统 , 基于`java`和`spring boot`，一眼就相中了这款，主要是因为跟自己的技术栈比较匹配，并且博客的风格也是十分简约，文档也比较全。

![2019-10-05-halo-blog-demo.png](https://i.loli.net/2019/10/05/9EBYyklnK6SopA7.png)

## 2. 搭建过程
一个简单的Web网站搭建，主要分为三个部分，分别是数据库、网站程序、主机和域名三个部分。现在讲究DevOps，各种应用已经虚拟化了，因此我也准备使用Docker的方式进行部署，方便运维和在不同的部署环境中迁移。
自己家里有黑群晖NAS，带有Docker，这个博客本身也有Docker镜像，因此搭建过程也非常简单了。
### 2.1 MySQL数据库
直接使用官方的镜像，我这里使用的是`mysql:5.7`版本，这里有几个参数需要设置的：
* 默认的数据库，通过环境变量：`MYSQL_DATABASE=blog`
* 默认的连接密码，通过环境变量设置：`MYSQL_ROOT_PASSWORD=whwdym.top`
* 将数据保存到宿主机: `-v  /docker/mysql/data/:/var/lib/mysql`
* 设置端口映射，方便通过客户端查看数据: `-p 3306:3306`
* 设置数据库字符串编码为`utf8mb4`，这里官方没有开放环境变量，需要需要通过配置文件配置，假设保存文件路径为`/docker/mysql/conf/mysql-config.cnf`，然后挂载到docker容器中：`-v /docker/mysql/conf/:/etc/mysql/conf.d`
```
[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4

[mysqld]
init_connect='SET collation_connection = utf8mb4_unicode_ci'
init_connect='SET NAMES utf8mb4'
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
skip-character-set-client-handshake
```


### 2.2 博客程序 
这里使用的镜像为：`ruibaby/halo:1.1.1`版本，这里也有几个地方需要设置：
* 链接到数据库容器：因为需要跟mysql数据库的容器进行通讯，因此运行博客程序的容器时，需要链接到数据库容器，假设数据库容器的名称为`blog_mysql_server`，通过参数`--link blog_mysql_server`进行配置，之后便可以通过主机名`blog_mysql_server`访问到数据库容器。
*  同样需要配置一下端口映射:`-p 8090:8090`
* `spring boot`配置文件：
```yaml
server:
  port: 8090
spring:
  datasource:
    # MySQL 配置
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://blog_mysql_server:3306/blog?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: whwdym.top
```
假设文件保存为：`/docker/halo/application.yaml`,然后挂载到容器中：`-v /docker/halo:/root/.halo`

这样配置完按顺序启动一下两个容器，然后访问:[http://localhost:8090/]就可以访问到我们博客了。

准备好docker容器之后，部署就很灵活了，你可以随意部署到任何一个支持docker的环境中，比如你可以购买阿里云的ECS，然后安装docker进行部署，我这里是安装自己家的黑群晖nas中。
### 2.3 互联网访问
以上部署完之后呢，只能在局域网内进行访问，如果需要开放到互联网，那么，你首先需要购买一个域名，这里选择的是`.top`域名，主要因为便宜，一年只需要9块钱，然后需要将域名解析到你部署的服务器，因为我是部署在家里的，没有公网IP，因此，我还需要一台有公网IP的ecs主机，来做反向代理（内网穿透）。
这里购买的是丐中丐版的ecs主机，内存512Mb，1核cpu，网络流量按量付费，一年只要几百块钱，已经是最便宜的主机了。注意这里要选择香港或者境外的域名，不然的话是需要备案才能提供web服务的。

反射代理这里用的是[frp](https://github.com/fatedier/frp)，用`C++`写的，比较稳定。
配置如下，即将`ECS公网IP:80`映射到`局域网服务器IP:8090`
```
[blog_web]
type = tcp
local_ip = 局域网服务器IP
local_port = 8090
remote_port = 80
```
这样，我们只需要将域名增加一条A记录，解析到ECS的公网IP即可，然后再配置ECS的80端口，允许入方向的访问。

## 3. 后记
![2019-10-05-halo-blog-architecture.png](https://i.loli.net/2019/10/05/a4P67yOr8jRWfuw.png)
这是最终部署完成的框架示意图，本次的博客搭建只是简单测试一下，如果真正上线一个网站，还涉及到`https`证书配置、`nginx`反向代理、`slb`负载均衡等配置，本文不再展开。
本文涉及到的配置文件以及Docker-compose文件，都可以在github中找到。

## 4. 参考文档
* MySQL镜像配置:https://hub.docker.com/_/mysql
* Halo配置:https://halo.run/guide/
* [frp反向代理:https://github.com/fatedier/frp/blob/master/README_zh.md]
* 本博客最终的Docker-compose文件: https://github.com/homeway88/halo_blog/blob/master/2019-10-05-halo-blog/README.md
