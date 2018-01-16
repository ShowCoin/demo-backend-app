package com.weipai.api;

import com.weipai.application.LiveStory;
import com.weipai.application.RankStory;
import com.weipai.common.Constant;
import com.weipai.common.Version;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.form.NewVideoPageForm;
import com.weipai.form.VideoPageForm;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.ManageService;
import com.weipai.service.RankService;
import com.weipai.service.RelationService;
import com.weipai.user.thrift.view.UserView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 */
@Controller
public class RankApi extends BaseApi {

	public static final Logger log = LoggerFactory.getLogger("RankApi");

	private final LiveStory liveStory;
	private final RankStory rankStory;
	private final RankService rankService;
	private final ManageService manageService;
	private final RelationService relationService;

	@Autowired
	public RankApi(ManageService manageService, RankService rankService, RelationService relationService,
				   LiveStory liveStory, RankStory rankStory) {
		this.manageService = manageService;
		this.rankService = rankService;
		this.relationService = relationService;
		this.liveStory = liveStory;
		this.rankStory = rankStory;
	}

	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/top_video")
	@ResponseBody
	public Map<String, Object> top_video(
			@RequestParam(value = "type") String type,
			@Validated VideoPageForm pageForm,
			@RequestParam(value = "city", required = false) String city,
			@RequestHeader("Client-Version") Version clientVersion,
			@RequestParam(value = "rand", defaultValue = "0") int rand,
			HttpServletRequest request)
			throws ReturnException {
		Map<String, Object> map = rankStory.findTopVideos(type, city, pageForm, clientVersion, rand);
		return success(map);
	}

	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/rank")
	@ResponseBody
	public Map<String, Object> rank(
			@RequestParam(value = "type") String type,
			@Validated NewVideoPageForm pageForm )
			throws ServiceException, ReturnException {
		if (! type.equals("latest"))
			return error("1002");


		Map<String, Object> result = new HashMap<>();

		List<Map> videos = rankService.getSquare("video", type
				, pageForm.getCursor(), pageForm.getCount(), null, 0);

		result.put("next_cursor", pageForm.getNextCursor(videos));
		result.put("video_list", videos);

		return success(result);
	}

	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/top_user")
	@ResponseBody
	public Map<String, Object> top_user(
			@RequestParam(value = "type", required = true) String type,
			@RequestParam(value = "cursor", required = false) Integer cursor,
			@RequestParam(value = "count", required = false) Integer count,
			@RequestParam(value = "city", required = false) String city)
			throws ServiceException, ReturnException {
		if (count==null || count < 0 || count>20) {
			count = 20;
		}
		if (cursor == null || cursor < 0
				|| cursor + count > Constant.RANK_LIMIT) {
			cursor = 0;
		}

		Map<String, Object> result = new HashMap<>();

		if (type.equals("top_city") && !Constant.ALL_CITIES.contains(city))
			return error("9002");

		List<Map> users = rankService.getSquare("user", type, cursor, count,
				city, 0);
		// 取得当前登录用户
		String uid = XThreadLocal.getInstance().getCurrentUser();
		List<String> userIds = null;
		if (users != null) {
			userIds = new ArrayList<String>();
			for (Map map : users) {
				Object userId = map.get("user_id");
				if (userId != null) {
					userIds.add(userId.toString());
				}else {
					userIds.add("0");
				}
			}
			if (userIds.size() > 0) {
				List<Boolean> focusList = relationService.isFollowed(uid,
						userIds);

				for (int i = 0; i < users.size(); i++) {
					Map user = users.get(i);
					try {
						boolean foucs = focusList.get(i);
						if (foucs) {
							user.put("follow_state", "1");
						} else {
							user.put("follow_state", "0");
						}
					} catch (Exception e) {
						log.error(e.getMessage(), e);
						user.put("follow_state", "0");
					}
				}

			}
		}else {
			users = new ArrayList<>();
		}
		String next_cursor = String.valueOf(cursor + count); 
		if (count>users.size()) {
			next_cursor = "";
		}
		result.put("next_cursor",next_cursor);
		result.put("user_list", users);

		return success(result);
	}

	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/get_citylist")
	@ResponseBody
	public Map<String, Object> get_citylist() throws ServiceException {
		Map result = new HashMap<String, Object>();

		result.put("hotToken", "1453190191");
		result.put("cityToken", "1405418480");
		result.put("hotList", Constant.HOT_CITIES);
		result.put("cityList", Constant.CITIES);

		String uid = XThreadLocal.getInstance().getCurrentUser();
		if (uid == null){
			result.put("currentCity", "北京市");
		}else{
			UserView userView = getUserView(uid);
			if (userView.getCity() == null || userView.getCity().equals("")) {
				result.put("currentCity", "北京市");
			}
			else {
				result.put("currentCity", userView.getCity());
			}
		}
		
		return success(result);
	}

	

	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/top_tuhaos")
	@ResponseBody
	public Map<String, Object> top_tuhao(
			@RequestParam(value = "next_cursor", required = false) Integer cursor,
			@RequestParam(value = "count", required = false) Integer count,
			@RequestParam(value = "type", required = true) String type)
			throws ServiceException, ReturnException {
		
		Map<String, Object> result = new HashMap<>();
		
		if (count==null || count < 0 || count>20) {
			count = 20;
		}
		if (cursor == null || cursor < 0
				|| cursor + count > Constant.RANK_LIMIT) {
			cursor = 0;
		}

		if (!Arrays.asList("day", "week", "month").contains(type))
			return error("1002");

		List<Map> videos = rankService.getPayRank("tuhao", type, cursor,
				count);
		String next_cursor = String.valueOf(cursor + count); 
		if (count>videos.size()) {
			next_cursor = "";
		}
		result.put("next_cursor",next_cursor);
		result.put("ranking", videos);

		return success(result);
	}

	/**
	 * 
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/top_stars")
	@ResponseBody
	public Map<String, Object> top_stars(
			@RequestParam(value = "next_cursor", required = false) Integer cursor,
			@RequestParam(value = "count", required = false) Integer count,
			@RequestParam(value = "type", required = true) String type)
			throws ServiceException, ReturnException {
		Map<String, Object> result = new HashMap<>();
		if (count==null || count < 0 || count>20) {
			count = 20;
		}
		if (cursor == null || cursor < 0
				|| cursor + count > Constant.RANK_LIMIT) {
			cursor = 0;
		}

		if (!Arrays.asList("day", "week", "month").contains(type))
			return error("1002");

		List<Map> videos = rankService.getPayRank("star", type, cursor,
				count);
		String next_cursor = String.valueOf(cursor + count); 
		if (count>videos.size()) {
			next_cursor = "";
		}

		result.put("ranking", videos);
		result.put("next_cursor",next_cursor);
		return success(result);
	}
}
