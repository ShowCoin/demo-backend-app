package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.infrastructure.file.ali.AliFileManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

import static java.util.Arrays.asList;

@Controller
@RequestMapping("/file")
public class FileApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(FileApi.class);

	final AliFileManager aliFileManager;

	@Autowired
	public FileApi(AliFileManager aliFileManager) {
		this.aliFileManager = aliFileManager;
	}

	@RequestMapping("/token")
	@ResponseBody
	@LoginRequired
	public Map token(
			@RequestHeader("Weipai-Userid") String currentUser,
            @RequestParam(value = "type") String type
	) throws Exception {
		if (! asList("video", "picture", "certification").contains(type))
			return error("1002");

		final Map<String, String> token = aliFileManager.getToken(currentUser, type);

		return success(token);
	}


}
