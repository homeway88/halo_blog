FROM maven:3.3.9-jdk-8

RUN mkdir -p /home/admin/CookieManager
ADD ./target/CookieManager-1.0.0.jar /home/admin/CookieManager
WORKDIR /home/admin/CookieManager
EXPOSE 8888

CMD ["java","-Xms32m","-Xmx62m","-jar","./CookieManager-1.0.0.jar"]
