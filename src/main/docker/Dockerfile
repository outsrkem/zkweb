#############################################################################
# zkweb
#
# 容器构建镜像
#  1. 使用命令将dockerfile和maven打包好的jar包放在target目录下：
#           $ mvn clean package -f pom-jar.xml
#
#  2. 在target目录下运行命令构建镜像：
#           $ docker build -t zkweb:v1.2.1 .
#
# 容器启动
#  1. 命令： docker run -p 8099:8099 --name zkweb -v /etc/localtime:/etc/localtime:ro zkweb:v1.2.1
#
#
#############################################################################


FROM registry.cn-shanghai.aliyuncs.com/outsrkem/jre:1.8.0_261

# 将本地文件夹挂载到当前容器

# 创建/tmp目录并持久化到Docker数据文件夹，因为Spring Boot使用的内嵌Tomcat容器默认使用/tmp作为工作目录。
RUN mkdir /opt/server

VOLUME ["/tmp"]

COPY zkWeb-v1.2.1.jar   /opt/server/app.jar
COPY docker-entrypoint.sh /bin/entrypoint.sh


EXPOSE 8099 8099
RUN chown 2000:2000 -R /opt/server \
    && chmod 600 /opt/server/app.jar \
    && chmod +x /bin/entrypoint.sh

USER server

WORKDIR "/opt/server"
ENTRYPOINT ["entrypoint.sh"]
CMD ["-jar","app.jar"]
