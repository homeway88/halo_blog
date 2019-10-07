
# 三行代码带你了解Tomcat的基本原理

![2019-10-06-tomcat-logo.jpeg](https://i.loli.net/2019/10/07/nvrsaLxwkyEWTXc.jpg)

毕业工作以来，一直在做跟Web相关的后端开发工作，也自称Web全栈工程师，虽然很多时候都在写CURD，对于一直在用tomcat和`Spring Mvc`也未真正深入了解过，趁国庆假期，看了一下tomcat的架构和代码，并对代码运行过程进行了跟踪，基本理清了它的原理和运行过程。

对于一次http的请求过程，无非是客户端跟服务端建立一个TCP连接，然后跟服务端发起一次GET/POST请求，服务端再返回对应的数据。这是简化之后的请求过程：
![2019-10-06-http-request.png](https://i.loli.net/2019/10/07/OGdVEBpC5P1zxs6.png)

那么，怎么实现这个程序呢？最简单的，应该有一部分，是负责底层的Socket连接与请求报文的数据解析，这块是tomcat的工作，然后将这个请求，再转给SpringMvc进行处理，SpringMvc根据请求路径，Dispatch到这个路径绑定的处理函数去处理，并返回数据，再由tomcat输出给客户端。那么，顺着这个思路，我下面这一过程进行跟踪，看是否跟自己的设想吻合。

## 0.准备工作
学习一个软件，最好的方法就是把它跑起来，并找到一条线索，打断点进行跟踪调试，这里我利用`Spring Initializer`初始化一个带有`Spring Web`的`Spring Boot`工程，下载后用`IDEA`打开，并在程序入口处增加了一个api，总共三行代码，然后直接DEBUG运行即可。
```java
@RequestMapping("/")
@ResponseBody
public String home(@RequestParam("name") String name) { return "Hello " + name; }
```

## 1. 分析过程
### 1.1 Tomcat对Socket连接的处理
程序运行起来之后，我们先将程序暂停一下，先分析一下，目前有哪些线程，并且这些线程停在哪里，可以看到有以下几个比较重要的线程，我们来逐个分析一下：
![2019-10-06-tomcat-threads.png](https://i.loli.net/2019/10/07/WQZw5H48iAFbONB.png)

#### 1.1.1  `http-nio-8080-Acceptor-0`
```java
//org.apache.tomcat.util.net.NioEndpoint
protected class Acceptor extends AbstractEndpoint.Acceptor {

	@Override
	public void run() {

		while (running) {
				//...
			try {
				SocketChannel socket = null;
				try {
					// Accept the next incoming connection from the server
					// socket
					socket = serverSock.accept();
				} catch (IOException ioe) {
					//...
				}
							// Configure the socket
				if (running && !paused) {
					// setSocketOptions() will hand the socket off to
					// an appropriate processor if successful
					if (!setSocketOptions(socket)) {
						closeSocket(socket);
					}
				} else {
					closeSocket(socket);
				}

			} catch (Throwable t) {
			
			}
		}
	}
}
```
可以看到，这里最重要的代码就是`serverSock.accept(); `，这里是tomcat接受客户端连接的入口，获取socket之后，再通过`setSocketOptions(socket) `将这个socket交给Poller线程；
#### 1.1.2 `http-nio-8080-ClientPoller-0`
```java
//org.apache.tomcat.util.net.NioEndpoint
public class Poller implements Runnable {

	/**
	 * The background thread that adds sockets to the Poller, checks the
	 * poller for triggered events and hands the associated socket off to an
	 * appropriate processor as events occur.
	 */
	@Override
	public void run() {
		// Loop until destroy() is called
		while (true) {

			boolean hasEvents = false;

			try {
				if (!close) {
					if (wakeupCounter.getAndSet(-1) > 0) {
						//if we are here, means we have other stuff to do
						//do a non blocking select
						keyCount = selector.selectNow();
					} else {
						keyCount = selector.select(selectorTimeout);
					}
				}
				
			} catch (Throwable x) {
			}
   
			Iterator<SelectionKey> iterator =
				keyCount > 0 ? selector.selectedKeys().iterator() : null;
			// Walk through the collection of ready keys and dispatch
			// any active event.
			while (iterator != null && iterator.hasNext()) {
				SelectionKey sk = iterator.next();
				NioSocketWrapper attachment = (NioSocketWrapper)sk.attachment();
				// Attachment may be null if another thread has called
				// cancelledKey()
				if (attachment == null) {
					iterator.remove();
				} else {
					iterator.remove();
					processKey(sk, attachment);
				}
			}//while
		}//while
	}
}
```

这里比较重要的就是`keyCount = selector.select(selectorTimeout);`，接受acceptor的socket，然后再调用`processKey(sk, attachment);`，将socket传给worker线程进行处理
#### 1.1.3 `http-nio-8080-exec-0`
```java
//org.apache.tomcat.util.net.NioEndpoint
protected class SocketProcessor extends SocketProcessorBase<NioChannel> {

	@Override
	protected void doRun() {
		NioChannel socket = socketWrapper.getSocket();
		// ...
	}
}
```
这里就进行拿到socket之后，就进行tomcat最主要的处理流程了。
#### 1.1.4 小结
看了这几个线程之后，对照下面这张图，就很好理解tomcat的Connector和nio模型了。
![2019-10-06-tomcat-nio.jpg](https://i.loli.net/2019/10/07/aFjAJth95mIuYLb.jpg)

* Acceptor：接收socket线程，这里虽然是基于NIO的connector，但是在接收socket方面还是传统的serverSocket.accept()方式，获得SocketChannel对象，然后封装在一个tomcat的实现类org.apache.tomcat.util.net.NioChannel对象中。然后将NioChannel对象封装在一个PollerEvent对象中，并将PollerEvent对象压入events queue里。这里是个典型的生产者-消费者模式，Acceptor与Poller线程之间通过queue通信，Acceptor是events queue的生产者，Poller是events queue的消费者。 
* Poller：Poller线程中维护了一个Selector对象，NIO就是基于Selector来完成逻辑的。在connector中并不止一个Selector，在socket的读写数据时，为了控制timeout也有一个Selector，在后面的BlockSelector中介绍。可以先把Poller线程中维护的这个Selector标为主Selector。 Poller是NIO实现的主要线程。首先作为events queue的消费者，从queue中取出PollerEvent对象，然后将此对象中的channel以`OP_READ`事件注册到主Selector中，然后主Selector执行select操作，遍历出可以读数据的socket，并从Worker线程池中拿到可用的Worker线程，然后将socket传递给Worker。整个过程是典型的NIO实现。 
* Worker ：Worker线程拿到Poller传过来的socket后，将socket封装在SocketProcessor对象中。然后从`Http11ConnectionHandler`中取出`Http11NioProcessor`对象，从`Http11NioProcessor`中调用`CoyoteAdapter`的逻辑。

## 1.2 http请求处理过程
在我们写的api中打上断点，并由浏览器发起一个请求，进到断点以后，我们就可以看到完整的处理栈了。
![2019-10-06-tomcat-stack.png](https://i.loli.net/2019/10/07/ImOKvTJeCSsVWHG.png)
这里我只列出一些比较重要的函数调用链，顺着这个调用链，可以看到，这里分为几部分，这个流程涉及到的代码太多，就不再放相关代码了，根据断点的调用链，点击之后即可看到相关的核心代码：
### 1.2.1 Connector处理部分
从`NioEndpoint$SocketProcessor` -> `Http11Processor ` -> `CoyoteAdapter `
	这里，主要是由`NioEndpoint$SocketProcessor `创建`Http11Processor `，通过调用`Http11Processor .service`对http请求进行处理，主要包括创建`org.apache.coyote.Request`与`org.apache.coyote.Response`两个对象，并对http的请求报文进行解析后保存在request中，最后，通过CoyoteAdapter的service方法，完成`org.apache.coyote.Request ` -> `org.apache.catalina.connector.Request (实现了HttpServletRequest)`和`org.apache.catalina.connector.Response (实现了HttpServletResponse）`的转换，并将这两个对象交接给Service进行处理。
### 1.2.2 Service处理部分
`StandardEngineValve` -> `StandardHostValve` ->  `StandardContextValve` -> `StandardWrapperValve ` -> `ApplicationFilterChain`
这一部分没有太多复杂的处理逻辑，但是涉及的代码还是非常多的，并且调用链也比较深，主要是通过责任链的模式，把这个请求一层一层往下传递，最终交给对应的Servlet进行处理。设计得这么复杂，主要是为了能够支持多个Service, Host等，并且在每个主要控制环节，都能非常灵活地组合各种逻辑，但从代码结构上来看，还是保持了各个节点功能的简一性，减少代码的直接耦合度，符合开闭原则。
### 1.2.3 SpringMvc处理部分
`HttpServlet ` -> `FrameworkServlet` -> `DispatcherServlet ` -> `RequestMappingHandlerAdapter ` -> `WebDemoApplication.home`
这部分主要是SpringMvc框架内部的处理流程，这里不再展开细解。

### 1.2.4 小结

![2019-10-06-tomcat-http-request.png](https://i.loli.net/2019/10/07/8GK1bJQyIRuLlkB.png)
根据以上的调用链，我们简单总结 一下这个调用过程：
1、用户发起请求`wget localhost:8080/?name=homeway`，Tomcat通过Connector中的Acceptor绑定8080端口并接收请求，然后通过Poller,Worker转交给`Http11Processor`解析出请求；
2、Connector把封装好的请求通过`CoyoteAdapter`转换成`HttpServletRequest`交由其所在的Service的Engine来处理，并等待Engine的回应；
3、Engine获得请求`localhost:8080/?name=homeway`，匹配所有的虚拟主机Host；
4、Engine匹配到名为`localhost`的Host（即使匹配不到也把请求交给该Host处理，因为该Host被定义为该Engine的默认主机），名为localhost的Host获得请求/，匹配它所拥有的所有的Context。Host匹配到路径为/的Context（如果匹配不到就把该请求交给路径名为 ""的Context去处理）；
5、path=“/”的Context获得请求/，在它的mapping table中寻找出对应的Servlet。
6、调用对应Servlet的service方法
7、Context把执行完之后的HttpServletResponse对象返回给Host；
8、Host把HttpServletResponse响应对象返回至Engine；
9、Engine将HttpServletResponse响应对象返回至Connector；
10、Connector将HttpServletResponse响应对象返回给客户端的浏览器。

## 总结 
![2019-10-06-tomcat-architecture.png](https://i.loli.net/2019/10/07/75ZCFrfXRVvWjwu.png)
有了以上分析过程，再来看tomcat的整体架构，是不是就容易理解了。之前也看过不少关于tomcat方面的介绍，但没真正跟过代码和处理流程，就只是停留在大致了解的程度，这次通过实践，把整个处理流程摸熟了之后，不仅对它的整体框架有了清晰的认识，也学习了它的一些设计思想，以后工作中如果遇到相关问题，也能更快地去定位问题，总之一句话：纸上得来终觉浅，绝知此事调代码。


## 参考文档：
* Tomcat架构分析:https://blog.csdn.net/Diamond_Tao/article/details/87389315
