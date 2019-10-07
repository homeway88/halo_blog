# 如何在shell中动态获取chrome浏览器的cookie信息

![2019-10-07-chrome.jpeg](https://i.loli.net/2019/10/07/2X7b1rKqecmCVv8.jpg)
## 0. 背景
在工作的时候，经常要接触一些办公系统，在网页上通过机械化的操作，来完成一个简单的功能，比如某台主机权限的申请，通过一套操作一下，大概7、8个步骤，花费30秒的时间，虽然不长，但是要脱离终端，到浏览器去操作，打断了心流，就感觉很烦人了。
我们在网页的操作，其实就是往这个网站的后台发起一个API请求，这个动作，我们在终端里面，通过curl命令也能完成，比如我们打开百度的首页，通过chrome的控制台 -> Network -> 找到对应的请示，右键，Copy -> Copy as cURL，我们就能得到如下的一条命令：
```shell
curl 'https://www.baidu.com/' -H 'Connection: keep-alive' -H 'Cache-Control: max-age=0' -H 'Upgrade-Insecure-Requests: 1' -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36' -H 'Sec-Fetch-Mode: navigate' -H 'Sec-Fetch-User: ?1' -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3' -H 'Sec-Fetch-Site: none' -H 'Accept-Encoding: gzip, deflate, br' -H 'Accept-Language: zh-CN,zh;q=0.9,en;q=0.8' -H 'Cookie: BIDUPSID=A10629EBE8B29EBEE170B7E4E405; PSTM=1520729161; BDUSS=pMV2FGNlNaUUdB134asdfCOVU5cHFCR0p2SzBtY0Q2OWlNZlhORHdhN1ZjQVFBQUFBJCQAAAAAABBBBBGHAAaG9tZXdheQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAPDejVzw3o1cck; ' --compressed
```
在终端执行这条命令，和你浏览器发起的这个请求，其实是等同的，但是这里面，有一个最重要的参数，就是你的Cookie信息，这个信息可以代表你当前在这个网站上的登录用户，如果我们要写一些自动化的网站操作脚本，或者写爬虫什么的，第一步就是怎么拿到cookie信息。

## 1. 获取chrome浏览器里的cookie
cookie信息，肯定是存储在chrome中，具体的存储位置未知，并且对于这么隐私的数据，应该也是会进行加密存储的，因此，我并没有去尝试通过读取cookie文件的方式。

想起之前用过一个模拟发请求的chrome插件，`postman`，通过安装`Postman Interceptor`拦截器，可以让我们在`postman`里模拟发请求的时候，自动带上网站的cookie信息，但是查阅了`postman`的相关资料，也没有开放接口让我们在其它地方可以拿到cookie信息。

### 1.1 通过chrome插件获取cookie数据
既然在浏览器插件里能拿到cookie，那么我们自己实现一个不就行了吗，拿到数据之后，再想办法把数据传出来就可以了。参考chrome api文档，我们可以通过添加一个cookies的监听器，来拿到变化的cookie，以及拿到某个domain域下的所有cookie，但是chrome也是运行在浏览器环境之上，无法直接往本地存储写数据，只能通过对外发起ajax请求来把数据传出去了。核心代码如下：
```javascript
function refreshDomain(domain) {
	chrome.cookies.getAll({domain: domain}, function (cookies) {
		// 这里就能拿到这个域下所有cookie了
		let all_cookies = cookies.filter(item => item.domain === domain)
			.map(item => item.name + "=" + item.value).join("; ");

		console.log("Report Cookie:domain=" + domain + ",cookies=" + all_cookies);
		$.ajax({
			 //这里需要一个http服务来接收数据
			url: "http://localhost:8888",
			method: "POST",
			data: {
				domain: domain,
				cookies: all_cookies
			},
			dataType: "json",
			success: function(data) { console.log("Report success:" + data) },
			failure: function (data) {
				console.log("Report failure:" + data)
			}
		})
	});
}

chrome.cookies.onChanged.addListener(function (event) {
	const cookie = event.cookie;
	refreshDomain(cookie.domain);
});
```

### 1.2 接收数据并存储
这里还需要实现一个http服务来接收插件发出来的cookie数据，这里我用spring boot初始化出来一个spring mvc的工程，再添加两个api，一个用于接收cookie并存储，一个用于对外再接供获取cookie信息的接口。代码如下：
```java

@SpringBootApplication
@EnableWebMvc
@Controller
public class CookieManager {
	private static Map<String, String> domainCookies = new HashMap<>(1024);

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public String getCookie(@RequestParam("domain") String domain) {
		return domainCookies.entrySet().stream()
			.filter(e -> domain.endsWith(e.getKey()))
			.map(Entry::getValue)
			.collect(Collectors.joining("; "));
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseBody
	public String setCookie(@RequestParam("domain") String domain,
							@RequestParam("cookies") String cookies) throws IOException {

		domainCookies.put(domain, cookies);

		return domain;
	}

	public static void main(String[] args) {
		SpringApplication.run(CookieManager.class, args);
	}
}
```
这里在获取cookie的时候做了一个处理，自动把父域的cookie带上。比如获取domain=www.baidu.com的cookie，会把domain=.baidu.com的数据也返回
### 1.3 在shell中的用法
```bash
COOKIE=$(wget localhost:8888/?domain=www.baidu.com -q -O -)
echo $COOKIE

curl 'https://www.baidu.com/' -H "Cookie:  $COOKIE"
```
注意这里`"Cookie:  $COOKIE" `必须是双引号，不能用单引号。
## 特别注意
cookie信息是一个非常隐私和重要的数据，虽然通过这个方法，能够将浏览器里面这个数据导出来，但对于这个数据，是需要特别小心保存的，cookie信息如果被别人拿到，相当于别人可以用你的身份做任何事情，这是非常危险的。因此本文只是作为一个例子，没有做任何加密，但在实际应用中，最好都做加密传输。
## 参考文档
* Postman： https://www.getpostman.com/
* Chrome api：https://developer.chrome.com/apps/api_index
* 本文代码：https://github.com/homeway88/halo_blog/tree/master/2019-10-06-cookie-manager