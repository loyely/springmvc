package com.zzw.ext.servlet;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;

import com.zzw.annotation.ExtController;
import com.zzw.annotation.ExtRequestMapping;
import com.zzw.utils.ClassUtil;

/**
 * 自定义前端控制器<br>
 * 手写springmvc 原理分析<br>
 * 1.创建一个前端控制器（）ExtDispatcherServlet 拦截所有请求(springmvc 基于servlet实现)<br>
 * ####2.初始化操作 重写servlet init 方法<br>
 * #######2.1 将扫包范围所有的类,注入到springmvc容器里面，存放在Map集合中 key为默认类名小写，value 对象<br>
 * #######2.2 将url映射和方法进行关联 <br>
 * ##########2.2.1 判断类上是否有注解,使用java反射机制循环遍历方法 ,判断方法上是否存在注解，进行封装url和方法对应存入集合中<br>
 * ####3.处理请求 重写Get或者是Post方法 <br>
 * ##########3.1
 * 获取请求url,从urlBeans集合获取实例对象,获取成功实例对象后,调用urlMethods集合获取方法名称,使用反射机制执行
 */
public class ExtDispatcherServlet extends HttpServlet {
	// springmvc 容器对象 key:类名id ,value 对象
	private ConcurrentHashMap<String, Object> springmvcBeans = new ConcurrentHashMap<String, Object>();
	// springmvc 容器对象 keya:请求地址 ,vlue类
	private ConcurrentHashMap<String, Object> urlBeans = new ConcurrentHashMap<String, Object>();
	// springmvc 容器对象 key:请求地址 ,value 方法名称
	private ConcurrentHashMap<String, String> urlMethods = new ConcurrentHashMap<String, String>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// 1.获取请求url地址
		String requestURI = req.getRequestURI();
		if (StringUtils.isEmpty(requestURI)) {
			return;
		}
		// 2.从urlbeans中取出bean对象
		Object object = urlBeans.get(requestURI);
		if (object == null) {
			resp.getWriter().println(" not found 404  url");
			return;
		}
		// 3.从urlMethods取出类的方法
		String methodName = urlMethods.get(requestURI);
		if (StringUtils.isEmpty(methodName)) {
			resp.getWriter().println(" not found method");
		}
		// 4.使用反射机制执行方法
		String resultPage = (String) methodInvoke(object, methodName);
		// 5.调用视图转换器渲染给页面展示
		extResourceViewResolver(resultPage, req, resp);

	}

	private void extResourceViewResolver(String pageName, HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// 根路径
		String prefix = "/";
		String suffix = ".jsp";
		req.getRequestDispatcher(prefix + pageName + suffix).forward(req, resp);

	}

	private Object methodInvoke(Object object, String methodName) {
		try {
			Class<? extends Object> classInfo = object.getClass();
			Method method = classInfo.getMethod(methodName);
			Object result = method.invoke(object);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void init() throws ServletException {
		// 1.获取当前包下的所有的类
		List<Class<?>> classes = ClassUtil.getClasses("com.zzw");
		// 2.将扫包范围所有的类,注入到springmvc容器里面，存放在Map集合中 key为默认类名小写，value 对象
		try {
			findClassMVCAnnotation(classes);
		} catch (Exception e) {
			e.getMessage();
		}
		// 3.将url映射和方法进行关联
		handlerMapping();
		System.out.println("wait");
	}

	// 3.将url映射和方法进行关联
	private void handlerMapping() {
		// 1.遍历springmvc bean容器 判断类上属否有url映射注解
		for (Map.Entry<String, Object> mvcBean : springmvcBeans.entrySet()) {
			// 2.遍历所有的方法上是否有url映射注解
			// 获取bean的对象
			Object object = mvcBean.getValue();
			// 获取类名
			// url映射地址
			String baseUrl = "";
			Class<? extends Object> classInfo = object.getClass();
			ExtRequestMapping declaredAnnotation = classInfo.getDeclaredAnnotation(ExtRequestMapping.class);
			if (declaredAnnotation != null) {
				// 获取类似的url地址
				baseUrl = declaredAnnotation.value();
			}

			Method[] declaredMethods = classInfo.getDeclaredMethods();
			for (Method method : declaredMethods) {
				ExtRequestMapping methodAnnotation = method.getDeclaredAnnotation(ExtRequestMapping.class);
				if (methodAnnotation != null) {
					String methodUrl = baseUrl + methodAnnotation.value();
					// springmvc 容器对象 key:请求地址 ,value类
					urlBeans.put(methodUrl, object);
					// springmvc 容器对象 key:请求地址 ,value 方法名称
					urlMethods.put(methodUrl, method.getName());
				}
			}
		}

	}

	// 2.将扫包范围所有的类,注入到springmvc容器里面，存放在Map集合中 key为默认类名小写，value 对象
	private void findClassMVCAnnotation(List<Class<?>> classes)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		for (Class<?> classInfo : classes) {
			// 判断类上是否有加上注解
			ExtController declaredAnnotation = classInfo.getDeclaredAnnotation(ExtController.class);
			if (declaredAnnotation != null) {
				// 默认类名是小写
				String beanId = ClassUtil.toLowerCaseFirstOne(classInfo.getSimpleName());
				// 实例化对象
				Object newInstance = ClassUtil.newInstance(classInfo);
				springmvcBeans.put(beanId, newInstance);
			}
		}

	}

}
