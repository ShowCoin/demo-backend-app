package com.weipai.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.weipai.common.Adapter;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.cache.RemoteCache;
import com.weipai.common.exception.ServiceException;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.manage.thrift.view.UserPayInfoView;
import com.weipai.search.thrift.view.UserSearchView;
import com.weipai.service.PushService;

@Controller
public class PushApi extends BaseApi {
	private static final Logger log = LoggerFactory.getLogger(PushApi.class);
	@Autowired
	private PushService pushService;
	
	/**
	 *
	 * @param title
	 * @param msg
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/test_push")
	@ResponseBody
	public Map<String, Object> test_push(@RequestParam(value = "title", required = true) String title,
			@RequestParam(value = "msg", required = false) String msg) throws ServiceException, Exception {
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		title = title.replace(" ", "");
		if (title.equals("")) {
			return success(result);
		}
		msg = msg.replace(" ", "");
		if (msg.equals("")) {
			return success(result);
		}
		pushService.testPush(msg, title);
		
		result.put("title", title);
		result.put("msg", msg);
		return success(result);
	
	}
}
