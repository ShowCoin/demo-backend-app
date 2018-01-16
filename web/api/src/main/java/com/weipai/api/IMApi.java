package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.common.Adapter;
import com.weipai.infrastructure.chatroom.qcloud.QCloudIMManager;
import com.weipai.infrastructure.chatroom.rong_cloud.RongCloudIMManager;
import com.weipai.user.thrift.view.UserView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/im")
public class IMApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(IMApi.class);

	private final RongCloudIMManager rongCloudIMManager;
	private final QCloudIMManager qCloudIMManager;

	@Autowired
	public IMApi(RongCloudIMManager rongCloudIMManager, QCloudIMManager qCloudIMManager) {
		this.rongCloudIMManager = rongCloudIMManager;
		this.qCloudIMManager = qCloudIMManager;
	}

	@RequestMapping("/token")
	@ResponseBody
	@LoginRequired
	public Map token(
	        @RequestParam(value = "type", defaultValue = "rongcloud") String type,
			@RequestHeader("Weipai-Userid") String currentUser
	) throws Exception {

		Map<String,Object> resultMap = new HashMap<>();

        UserView userView = getUserView();
		String token;
        if ("rongcloud".equals(type)) {
			token = rongCloudIMManager.getUserToken(currentUser, userView.getNickname(), Adapter.getAvatar(userView.getProfileImg()));
		}
		else {
			token = qCloudIMManager.getUserToken(currentUser, userView.getNickname(), Adapter.getAvatar(userView.getProfileImg()));
		}

		resultMap.put("token", token);

		return success(resultMap);
	}


}
