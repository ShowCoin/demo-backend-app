package com.weipai.api;

import com.google.common.collect.ImmutableMap;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.LiveStory;
import com.weipai.common.Adapter;
import com.weipai.common.Constant;
import com.weipai.common.Constant.STAT_ACTION;
import com.weipai.common.Event;
import com.weipai.common.GpsUtil;
import com.weipai.common.JacksonUtil;
import com.weipai.common.ParallelHandler;
import com.weipai.common.TimeUtil;
import com.weipai.common.Version;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.client.kafka.KafkaProperties;
import com.weipai.common.client.kafka.Producer;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.form.PageForm;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.manage.thrift.view.ReportView;
import com.weipai.message.thrift.view.MessageView;
import com.weipai.pay.thrift.view.UserPayInfoView;
import com.weipai.relation.thrift.view.LikeView;
import com.weipai.service.IndexService;
import com.weipai.service.ManageService;
import com.weipai.service.MessageService;
import com.weipai.service.PayService;
import com.weipai.service.RelationService;
import com.weipai.service.StatService;
import com.weipai.service.VideoService;
import com.weipai.stat.thrift.view.UserStatView;
import com.weipai.stat.thrift.view.VideoStatView;
import com.weipai.struc.HeaderParams;
import com.weipai.struc.TopCommentParam;
import com.weipai.struc.VideoInfoParam;
import com.weipai.user.thrift.view.UserView;
import com.weipai.video.thrift.view.VideoView;

import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RelationApi extends BaseApi {

	//region member
	private static final Logger log = LoggerFactory
			.getLogger(RelationApi.class);

	private final RelationService relationService;
	private final StatService statService;
	private final VideoService videoService;
	private final ManageService manageService;
	private final PayService payService;
	private final MessageService messageService;
	private final IndexService indexService;
	private final LiveStory liveStory;

	@Autowired
	public RelationApi(RelationService relationService, StatService statService, VideoService videoService, ManageService manageService, PayService payService, MessageService messageService, IndexService indexService, LiveStory liveStory) {
		this.relationService = relationService;
		this.statService = statService;
		this.videoService = videoService;
		this.manageService = manageService;
		this.payService = payService;
		this.messageService = messageService;
		this.indexService = indexService;
		this.liveStory = liveStory;
	}
	//endregion

	//region 关注关系
	/**
	 * 一键关注多个用户
	 * @param fids
	 * @return
	 */
	@RequestMapping(value = "/follow_users")
	@ResponseBody
	public Map<String, String> follows(@RequestParam(required = true, value = "to_user_ids") String fids) throws ReturnException {

		// 当前登陆用户
		String uid = XThreadLocal.getInstance().getCurrentUser();
		if (uid == null) {
			return error("2019");
		}

		if(isForbidden(uid, Constant.USER_AUTH_FORBID.FOLLOW_PERSON.getIndex())){
			return error("5002");
		}

		String[] toUsers = fids.split(",");
		for(String fid : toUsers){
			try {
				relationService.follow(uid, fid);
			} catch (ServiceException e) {
				log.error(e.getMessage(), e);
			}
		}

		return success();
	}

	/**
	 * 关注、取消关注
	 * @param fid
	 * @param type
	 * @param liveId
	 * @return
	 * @throws ReturnException
	 */
	@RequestMapping(value = "/follow_user")
	@ResponseBody
	public Map<String, String> follow(
			@RequestParam("to_user_id") String fid,
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "live_id", required = false) String liveId
	) throws ReturnException {

		// 当前登陆用户
		String uid = XThreadLocal.getInstance().getCurrentUser();
		Map<String, String> x = checkUser(fid, uid);
		if (x != null) return x;

		Map<String, String> map = new HashMap<>();
		switch (type) {
			case "add":
				map.put("follow_state", "1");
				try {
					relationService.follow(uid, fid);

					Map<String, Object> event = new HashMap<>();
					event.put("uid", uid);
					event.put("fid", fid);
					event.put("time", TimeUtil.getCurrentTimestamp());
					Producer.getInstance().sendData(Event.E_FOLLOW.name(), JacksonUtil.writeToJsonString(event));

					if (liveId != null && !"".equals(liveId))
					    liveStory.follow(liveId, uid, fid);

				} catch (ServiceException e) {
					log.error(e.getMessage(), e);
					return error("0");
				}
				break;
			case "delete":
				map.put("follow_state", "0");
				try {
					relationService.unfollow(uid, fid);
				} catch (ServiceException e) {
					log.error(e.getMessage(), e);
					return error("0");
				}
				break;
			default:
				return error("1002");
		}

		setStatInfo(fid, uid, map);
		return success(map);
	}

	private void setStatInfo(@RequestParam("to_user_id") String fid, String uid, Map<String, String> map) {
		UserStatView fromUserStat = null;
		try {
			fromUserStat = statService.findUserStatByUid(uid);
		} catch (ServiceException e1) {
		}
		map.put("from_user_follow_num", String.valueOf(fromUserStat == null ? 0
				: fromUserStat.getFollow()));

		UserStatView toUserStat = null;
		try {
			toUserStat = statService.findUserStatByUid(fid);
		} catch (ServiceException e) {
		}
		map.put("to_user_fans_num",
				String.valueOf(toUserStat == null ? 0 : toUserStat.getFans()));
	}

	private Map<String, String> checkUser(@RequestParam("to_user_id") String fid, String uid) throws ReturnException {
		if (uid == null) {
			return error("2019");
		}
		if(isForbidden(uid, Constant.USER_AUTH_FORBID.FOLLOW_PERSON.getIndex())){
			return error("5002");
		}

		if (uid.equals(fid)){
			return error("2100");
		}
		return null;
	}

	/**
	 * 获取用户关注列表
	 *
	 * @param uid
	 * @param cursor
	 * @param count
	 * @param relative
	 * @return
	 */
	@RequestMapping(value = "/user_follow_list")
	@ResponseBody
	public Map followList(
			@RequestParam(required = true, value = "uid") String uid,
			@RequestParam(required = false, value = "cursor") Integer cursor,
			@RequestParam(required = false, value = "gender") String gender,
			@RequestParam(required = true, value = "count") Integer count,
			@RequestParam(required = false, value = "relative") String relative) {

	    	if (cursor==null||cursor < 0){
	    		cursor = 0;
	    	}
	    	if (count==null || count < 0 || count>20) {
				count = 20;
			}

		List<UserView> userViewList = null;
		try {
			userViewList = relationService.findFollowListByUid(uid, cursor,
					count);
		} catch (ServiceException e) {
			log.error(e.getMessage(), e);
		}

		Map map = getMapData(cursor, count, userViewList);

		return success(map);
	}

	/**
	 * 获取用户粉丝列表
	 *
	 * @param uid
	 * @param cursor
	 * @param count
	 * @param relative
	 * @return
	 */
	@RequestMapping(value = "/user_fans_list")
	@ResponseBody
	public Map fansList(
			@RequestParam(required = true, value = "uid") String uid,
			@RequestParam(required = false, value = "cursor") Integer cursor,
			@RequestParam(required = false, value = "gender") String gender,
			@RequestParam(required = true, value = "count") Integer count,
			@RequestParam(required = false, value = "relative") String relative) {

	    	if (cursor==null||cursor < 0){
	    		cursor = 0;
	    	}
	    	if (count==null || count < 0 || count>20) {
				count = 20;
			}
		List<UserView> userViewList = null;
		try {
			userViewList = relationService
					.findFansListByUid(uid, cursor, count);


		} catch (ServiceException e) {
			log.error(e.getMessage(), e);
		}

		Map map = getMapData(cursor, count, userViewList);

		return success(map);
	}

	/**
	 * @param cursor
	 * @param count
	 * @param userViewList
	 * @return
	 */
	private Map getMapData(Integer cursor, Integer count,
			List<UserView> userViewList) {
		String next_cursor = "";
		List<Map<String, Object>> userList = new ArrayList<>();
		if (userViewList != null) {
			if (userViewList.size() == count) {
				next_cursor = String.valueOf(cursor + count);
			}

			List<String> uids = new ArrayList();
			for (UserView userView : userViewList) {
				if (userView == null || userView.getId() == null){
					continue;
				}
				Map<String, Object> map = new HashMap<>();
				map.put("user_id", String.valueOf(userView.getId()));
				map.put("nickname", userView.getNickname());
				map.put("gender", userView.getSex4Display());
				map.put("avatar_url", userView.getProfileImg());
				map.put("follow_state", "0");
				map.put("intro", userView.getMood());
				map.put("level", userView.getLevel());
				map.put("age", userService.getAge(userView));
				map.put("city", userView.getCity());
				map.put("avatar", userView.getProfileImg());

				try {
					final String uid = userView.getId();
					//查询消费表中vip状态
					UserPayInfoView userPayInfo = new LocalCache<UserPayInfoView>() {
						@Override
						public UserPayInfoView getAliveObject()
								throws Exception {
							return payService.findUserPayInfo(uid);
						}
					}.put(5, "user_payinfo_" + uid);

					int vipExpire = 0;
					if (userPayInfo != null&&!StringUtils.isEmpty(userPayInfo.getVipExpire())) {
						vipExpire = Integer.parseInt(userPayInfo.getVipExpire());
					}
					map.put("is_vip", Adapter.isVip(vipExpire));
				} catch (Exception e) {
					log.error(e.getMessage(),e);
					map.put("is_vip", "0");
				}

				String distance = "";
				HeaderParams headerParams = XThreadLocal.getInstance().getHeaderParams();
				Double centerLon = null;
				Double centerLat = null;

				if (userView.getLng() > 0 || userView.getLat() > 0){
					if (headerParams.getLongitude() != null || headerParams.getLatitude() != null){
						try {
							centerLon = Double.parseDouble(headerParams.getLongitude());
							centerLat = Double.parseDouble(headerParams.getLatitude());
							Double dis = GpsUtil.distance(centerLon, centerLat, userView.getLng(), userView.getLat());
							java.text.DecimalFormat  df = new   java.text.DecimalFormat("#.#");
							if (dis <= 1000){
								distance = df.format(dis) + "米";
							}else{
								Double gl = dis/1000;
								distance = df.format(gl) + "公里";
							}

						} catch (Exception e) {
						}

					}
				}

				map.put("distance", distance);
				userList.add(map);
				uids.add(userView.getId());
			}

			try {
				if (uids!= null && uids.size() > 0){
					String currentUser = XThreadLocal.getInstance().getCurrentUser();
					if (currentUser != null){
						List<Boolean> followedList = relationService.isFollowed(currentUser, uids);
						for (int i=0; i<followedList.size(); i++) {
							userList.get(i).put("follow_status", followedList.get(i)?"1":"0");
						}
					}

				}

			} catch (ServiceException e) {
				log.error(e.getMessage(), e);
			}

		}

		Map map = new HashMap();

		map.put("next_cursor", next_cursor);
		map.put("user_list", userList);
		return map;
	}
	//endregion

	//region 喜欢关系
	/**
	 * 获取用户喜欢的视频列表
	 *
	 * @param uid
	 * @param cursor
	 * @param count
	 * @param relative
	 * @return
	 */
	@RequestMapping(value = "/my_favorite_video_list")
	@ResponseBody
	public Map myFavoriteVideoList(
			@RequestParam(required = false, value = "user_id") String user_id,
			@RequestParam(required = false, value = "cursor") Integer cursor,
			@RequestParam(required = false, value = "count") Integer countNum,
			@RequestHeader("Client-Version") final Version clientVersion,
			@RequestParam(required = false, value = "relative") String relative) throws ReturnException {

    	if (cursor==null||cursor < 0){
    		cursor = 0;
    	}
		if (countNum==null||countNum < 0 || countNum > 50) {
			countNum = 20;
		}
		// 取得当前登录用户
		String currentUser = XThreadLocal.getInstance().getCurrentUser();
		String target = currentUser;
		if (user_id == null) {
			if (currentUser == null) {
				return error("2019");
			}
		} else {
			target = user_id;

		}
		final String uid =target;
		final int start = cursor;
		final int count = countNum;
		List<VideoInfoParam> resultList = new ArrayList<VideoInfoParam>();
		List<VideoInfoParam> videoInfoList = new ArrayList<VideoInfoParam>();
		try {

			List<String> vids = new LocalCache<List<String>>() {
				@Override
				public List<String> getAliveObject() throws Exception {
					List<String> list = new ArrayList<String>();
					List<LikeView> videoList = relationService
							.findLikeRelationByUid(uid, start, count);
					for (LikeView likeView : videoList) {
						try {
							list.add(likeView.getVid());
						} catch (Exception e) {
							log.error(e.getMessage(), e);
						}
					}
					return list;
				}

			}.put(60, "vids" + uid);

			videoInfoList = new ParallelHandler<String, VideoInfoParam>() {

				@Override
				public VideoInfoParam handle(String item) {
					try {
						return videoService.findVideoPageInfo(item, uid, clientVersion);
					} catch (Exception e) {
						log.error(e.getMessage(), e);
					}
					return null;
				}
			}.execute(vids);

			if (videoInfoList!=null) {
				//添加手机类型
				final String phoneType = XThreadLocal.getInstance().getHeaderParams().getPhoneType();
				for (VideoInfoParam videoInfoParam : videoInfoList) {
					if (videoInfoParam==null) {
						continue;
					} else if (videoInfoParam.getPosted() == 1){
						resultList.add(videoInfoParam);  // 非空内容加入结果
					}

					List<TopCommentParam> commentList = videoInfoParam.getTop_reply_list();
					if (commentList==null) {
						continue;
					}
					CollectionUtils.forAllDo(commentList, new Closure<TopCommentParam>() {
						@Override
						public void execute(TopCommentParam topCommentParam) {
							topCommentParam.setComment_from_device(phoneType);
						}
					});
				}
			}

		} catch (Exception e) {
			return error("0");
		}

		Map<String, Object> resultMap = new HashMap<String, Object>();
		String next_cursor = String.valueOf(cursor + count);
		if (count>videoInfoList.size()) {
			next_cursor = "";
		}
		resultMap.put("next_cursor", next_cursor);
		resultMap.put("prev_cursor",String.valueOf(cursor));
		resultMap.put("video_list", resultList);
		return success(resultMap);
	}
	/**
	 * 我喜欢的视频列表
	 *
	 * @param uid
	 * @param cursor
	 * @param count
	 * @param relative
	 * @return
	 */
	@RequestMapping(value = "/user/likes")
	@ResponseBody
	@LoginRequired
	public Map userLikes(
			@Validated final PageForm pageForm,
			@RequestHeader("Weipai-Userid") final String currentUser) throws ReturnException {
		try {
			List<LikeView> videoList = relationService
					.findLikeRelationByUid(currentUser, pageForm.getCursor(), pageForm.getCount());


			List<String> vids = (List<String>) CollectionUtils.collect(videoList, new Transformer<LikeView, String>() {
				@Override
				public String transform(LikeView likeView) {
					return likeView.getVid();
				}
			});

			List list = videoService.renderVideoList(vids);
			Map<String, Object> result = new HashMap<>();
			result.put("videos", list);
			result.put("next_cursor", pageForm.getNextCursor(videoList));
			return success(result);
		} catch (ServiceException e) {
			log.error(e.getMessage(), e);
			return error();
		}
	}

	/**
	 * 喜欢一个视频
	 * @param type
	 * @param vid
	 * @param userId
	 * @param videoUid
	 * @return
	 */
	@RequestMapping(value = "/like_video")
	@ResponseBody
    @LoginRequired
	public Map likeVideo(
			@RequestParam(value = "blog_id") String vid,
			@RequestParam(value = "tid") String tid,
			@RequestHeader("Weipai-Userid") String uid
	) throws ReturnException {

		if(isForbidden(XThreadLocal.getInstance().getCurrentUser(), Constant.USER_AUTH_FORBID.LIKE_VIDEO.getIndex())){
			return error("5002");
		}

		Map<String, Object> resultMap = new HashMap<String, Object>();

		try {
			relationService.like(uid, vid);

			if (uid.equals(Constant.SYSTEM_USER.XIAO_MA)) {
				indexService.updateVideoIndex(vid, ImmutableMap.of(
						"ma_mark", "1"
						, "ma_mark_at", TimeUtil.getCurrentTimestampAsStr()
				));
			}

			VideoStatView videoStatView = statService.findVideoStatByVid(vid);
			resultMap.put("like_state", "1");
			resultMap.put("like_num", videoStatView == null?1:videoStatView.getLiked()+1);

			try {
				Map map = new HashMap();
				map.put("uid", uid);
				map.put("vid", vid);
				map.put("tid", tid);
				map.put("action", STAT_ACTION.LIKE.toString());
				map.put("time", (int) (System.currentTimeMillis() / 1000));
				Producer.getInstance().sendData(KafkaProperties.statTopic,
						JacksonUtil.writeToJsonString(map));
			} catch (Exception e) {
				log.error(" --------- kafka message error ---------"+e.getMessage(),e);
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
			return error("0");
		}
		try {
			VideoView videoView=videoService.findVideoByVid(vid, null);
			if(videoView!=null){
				UserView userView = getUserView(uid);
				MessageView messageView = new MessageView();
				messageView.setCreateTime((int)(System.currentTimeMillis()/1000));
				messageView.setFromNickName(userView.getNickname());
				messageView.setFromUid(XThreadLocal.getInstance().getCurrentUser());
				messageView.setReadStatus(0);
				messageView.setMsgType(Constant.MESSAGE_TYPE.LIKE.getCode());
				messageView.setVid(vid);
				messageView.setMsg(userView.getNickname()+"喜欢了你的视频");
				messageView.setUid(videoView.getUser());
				messageService.saveMessageView(messageView);
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
		return success(resultMap);
	}

	/**
	 * 取消喜欢一个视频
	 * @param type
	 * @param vid
	 * @param userId
	 * @param videoUid
	 * @return
	 */
	@RequestMapping(value = "/cancel_like_video")
	@ResponseBody
	@LoginRequired
	public Map cancelLikeVideo(
			@RequestParam(value = "blog_id") String vid,
			@RequestParam(value = "tid") String tid,
			@RequestHeader("Weipai-Userid") String currentUser
	) throws ReturnException {
		try {
			int ret = relationService.cancelLike(currentUser, vid);

			// 取消喜欢后，喜欢数应减一（仅当状态改变的时候）
		} catch (Exception e) {
			log.error(e.getMessage(),e);
			return error("0");
		}

		return success();
	}
	//endregion

	//region 举报
	/**
	 * 举报
	 * @param type
	 * @param vid
	 * @param userId
	 * @param videoUid
	 * @return
	 */
	@RequestMapping(value = "/report")
	@ResponseBody
	public Map report(
			@RequestParam(required = true, value = "type") String type,
			@RequestParam(required = true, value = "reported") String reported,
			@RequestParam(required = true, value = "content") String content) throws ReturnException {

		if (StringUtils.isEmpty(type)||StringUtils.isEmpty(reported)||StringUtils.isEmpty(content)) {
			return error("1002");
		}
		// 取得当前登录用户
		UserView user = getUserView(XThreadLocal.getInstance().getCurrentUser());
		if (user == null) {
			return error("2019");
		}
		Map<String, Object> resultMap = new HashMap<String, Object>();
		final String uid = user.getId();
		try {
			ReportView reportView = new ReportView();
			reportView.setType(type);
			reportView.setContent(content);
			reportView.setDeleted(0);
			reportView.setHandled(0);
			reportView.setReported(reported);
			reportView.setTime((int)(System.currentTimeMillis()/1000l));
			reportView.setUid(uid);
			manageService.saveReport(reportView);
		} catch (Exception e) {
			return error("0");
		}
		return success(resultMap);
	}
	//endregion
}