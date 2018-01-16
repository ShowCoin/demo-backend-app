package com.weipai.api;

import com.google.common.collect.ImmutableMap;

import com.weipai.annotation.LoginRequired;
import com.weipai.comment.thrift.view.CommentView;
import com.weipai.common.Constant;
import com.weipai.common.Constant.COMMENT_OP_TYPE;
import com.weipai.common.Constant.STAT_ACTION;
import com.weipai.common.JacksonUtil;
import com.weipai.common.TimeUtil;
import com.weipai.common.Version;
import com.weipai.common.cache.RemoteCache;
import com.weipai.common.client.kafka.KafkaProperties;
import com.weipai.common.client.kafka.Producer;
import com.weipai.common.client.redis.JedisUtil;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.common.id.ObjectId;
import com.weipai.form.PageForm;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.message.thrift.view.MessageView;
import com.weipai.service.CommentService;
import com.weipai.service.MessageService;
import com.weipai.service.SensitiveService;
import com.weipai.struc.TopCommentParam;
import com.weipai.user.thrift.view.UserView;
import com.weipai.video.thrift.view.VideoView;

import org.apache.commons.collections4.Closure;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class CommentApi extends BaseApi {

	private static final Logger log = LoggerFactory.getLogger(CommentApi.class);
	@Autowired
	private CommentService commentService;
	@Autowired
	private SensitiveService sensitiveService;
	@Autowired
	private MessageService messageService;

	@RequestMapping(value = "/blog_comment_list")
	@ResponseBody
	public Map blogCommentList(
			@RequestParam(value = "blog_id") final String blogId,
			@RequestParam(value = "cursor", defaultValue = "0") Integer cursor,
			@RequestParam(value = "count") Integer count,
			@RequestHeader("Client-Version") Version clientVersion,
			@RequestHeader("os") String os
			) throws ReturnException {

		if (StringUtils.isEmpty(blogId)) {
			return error("1002");
		}

		if (cursor<0) {
			cursor = 0;
		}
		if (count < 0 || count>20) {
			count = 20;
		}

		Map<String, Object> resultMap= new HashMap<>();

		List<TopCommentParam> dataList;
		try {
			Map<String, String> equalCondition = new HashMap<String, String>();
			equalCondition.put("deleted", "0");
			if ((os.equals(Constant.OS.ANDROID) && clientVersion.compareTo(new Version("1.2")) >= 0)
				|| (os.equals(Constant.OS.IOS) && clientVersion.compareTo(new Version("5.3")) >= 0)) {
				dataList = commentService.findCommentListByVid(blogId, equalCondition, cursor, count);
			}
			else {
				Map<String, String> neCondition = new HashMap<String, String>();
				neCondition.put("phone_type", "system");
				dataList = commentService.findCommentListByVidWithNECondition(blogId, equalCondition,neCondition, cursor, count);
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
			return error("0");
		}
		if (dataList==null) {
			dataList = new ArrayList<>();
		}

		String next_cursor = String.valueOf(cursor + count);
		if (count > dataList.size()) {
			next_cursor = "";
		}

		resultMap.put("comment_list", dataList);
		resultMap.put("next_cursor",next_cursor);
		resultMap.put("prev_cursor",String.valueOf(cursor));
		return success(resultMap);
	}

	@RequestMapping(value = "/blog_comment")
	@ResponseBody
	public Map blogComment(
			@RequestParam(value = "blog_id") String vid,
			@RequestParam(value = "comment_id", required = false) String commentId,
			@RequestParam(value = "content", required = false) String content,
			@RequestParam(value = "reply_id", required = false) String replyId,
			@RequestParam(value = "type") String type,
			@RequestHeader("os") String os,
			HttpServletRequest request,HttpServletResponse response) throws Exception {

		response.setContentType("text/html; charset=UTF-8");
		if (StringUtils.isEmpty(vid)||StringUtils.isEmpty(type)) {
			return error("1002");
		}

		Map<String, Object> resultMap= new HashMap<String, Object>();
		//取得当前登录用户
		UserView user = getUserView(XThreadLocal.getInstance().getCurrentUser());
		if(user==null){
			return error("2019");
		}

		if(isForbidden(XThreadLocal.getInstance().getCurrentUser(), Constant.USER_AUTH_FORBID.PUBLISH_COMMENT.getIndex())){
			return error("5002");
		}

		//新增评论和回复评论都走新增
		if (type.equals(COMMENT_OP_TYPE.ADD.getTypeName())||type.equals(COMMENT_OP_TYPE.REPLY.getTypeName())) {

			Map error = addComment(vid, content, replyId, type, request, resultMap, user, os);
			if (error != null) return error;

		}else if (type.equals(COMMENT_OP_TYPE.DELETE.getTypeName())) {
			//删除评论 设置评论删除字段为 0
			if (commentId.isEmpty()) {
				return error("1002");
			}
			CommentView commentView = commentService.getCommentById(vid, commentId);
			vid = commentView.getVid();
			VideoView videoView = videoService.findVideoByVid(vid, null);
			if (!user.getId().equals(videoView.getUser())&&!user.getId().equals(commentView.getUid())) {
				return error("10001");
			}

			//标识前端用户删除的评论
			String delby = "self";
			int delete_at = (int) (System.currentTimeMillis() / 1000);
			commentService.deleteComment(vid, commentId, delby,delete_at);

			try {
				Map map = new HashMap();
				map.put("vid", vid);
				map.put("tid", videoView.getUser());
				map.put("action", STAT_ACTION.COMMENT_DEL.toString());
				map.put("time", (int) (System.currentTimeMillis() / 1000));
				Producer.getInstance().sendData(KafkaProperties.statTopic,
						JacksonUtil.writeToJsonString(map));
			} catch (Exception e) {
				log.error(" --------- kafka message error ---------"+e.getMessage(),e);
			}
		}else {
			return error("1002");
		}
		//清除评论列表缓存
		RemoteCache.remove("commentList_all_"+vid);
		//清除视频详情缓存
		RemoteCache.remove("videoInfo_"+vid);
		//视频区三条评论
		Map<String, String> map = new HashMap<String, String>();
		map.put("deleted", "0");
		final Map<String, String> paramMap = map;

		List<TopCommentParam> topCommentList = null;
		try {
			topCommentList = commentService.findCommentListByVid(vid,paramMap, 0, Constant.COMMENT_TOP_NUM);
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
		if (topCommentList==null) {
			topCommentList = new ArrayList<TopCommentParam>();
		}
		//添加手机类型
		final String phoneType = XThreadLocal.getInstance().getHeaderParams().getPhoneType();
		CollectionUtils.forAllDo(topCommentList, new Closure<TopCommentParam>() {
			@Override
			public void execute(TopCommentParam topCommentParam) {
				topCommentParam.setComment_from_device(phoneType);
			}
		});

		//评论总数
		int commentCount = 0 ;
		try {
			commentCount = commentService.findCommentCountByVid(vid, paramMap);
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		}
		resultMap.put("comment_count", String.valueOf(commentCount));
		resultMap.put("top_reply_list", topCommentList);

		return success(resultMap);
	}

	private Map addComment(String vid, String content, String replyId, String type, HttpServletRequest request, Map<String, Object> resultMap, UserView user, String os) throws Exception {

		String uid = user.getId();

		String key = String.format("weipai:comment:%s:%s:%d", vid, uid, content.hashCode());
		String ret    = JedisUtil.set(key, "1", "nx", "ex", 300);
		if (!ret.equals("OK"))
			return error("4001");

		if(!content.equals(sensitiveService.filter(content, 1))){
            return error("4002");
        }

		if (StringUtils.isEmpty(content)) {
            return error("1002");
        }
		CommentView commentView = new CommentView();
		String      cId         = ObjectId.get().toString();
		commentView.setId(cId);
		commentView.setVid(vid);
		commentView.setCreatedAt(TimeUtil.getCurrentTimestamp());
		commentView.setDeleted(0);
		commentView.setActive(0);
		commentView.setContent(content);

		commentView.setUid(user.getId());
		commentView.setProfileImg(user.getProfileImg());
		//commentView.setIsVip(user.is_vip==true?1:0);
		commentView.setNickname(user.getNickname());
		//获得登录设备信息
		commentView.setPhoneType(request.getHeader("Phone-Type").toString());
		commentService.saveComment(commentView);
		resultMap.put("commentId", cId);

		try {
            VideoView videoView = videoService.findVideoByVid(vid, null);
            Map       map       = new HashMap();
            map.put("vid", vid);
            map.put("tid", videoView.getUser());
            map.put("action", STAT_ACTION.COMMENT.toString());
            map.put("time", (int) (System.currentTimeMillis() / 1000));
            Producer.getInstance().sendData(KafkaProperties.statTopic,
                    JacksonUtil.writeToJsonString(map));
        } catch (Exception e) {
            log.error(" --------- kafka message error ---------"+e.getMessage(),e);
        }
		try {
            if(type.equals(COMMENT_OP_TYPE.REPLY.getTypeName())){
                if(replyId!=null&&!"".equals(replyId)){
                    MessageView messageView = new MessageView();
                    messageView.setCreateTime((int)(System.currentTimeMillis()/1000));
                    messageView.setFromNickName(user.getNickname());
                    messageView.setFromUid(XThreadLocal.getInstance().getCurrentUser());
                    messageView.setReadStatus(0);
                    messageView.setMsgType(Constant.MESSAGE_TYPE.COMMENT.getCode());
                    messageView.setMsg(user.getNickname()+"评论了你的视频");
                    messageView.setVid(vid);
                    messageView.setUid(replyId);
                    messageService.saveMessageView(messageView);
                }
            }else{
                VideoView videoView=videoService.findVideoByVid(vid, null);
                if(videoView!=null){
                    MessageView messageView = new MessageView();
                    messageView.setCreateTime((int)(System.currentTimeMillis()/1000));
                    messageView.setFromNickName(user.getNickname());
                    messageView.setFromUid(XThreadLocal.getInstance().getCurrentUser());
                    messageView.setReadStatus(0);
                    messageView.setMsgType(Constant.MESSAGE_TYPE.COMMENT.getCode());
                    messageView.setMsg(user.getNickname()+"评论了你的视频");
                    messageView.setUid(videoView.getUser());
                    messageView.setVid(vid);
                    messageService.saveMessageView(messageView);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
		return null;
	}

	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/reply")
	@ResponseBody
	@LoginRequired
	public Map<String, Object> reply(
			@RequestParam("comment_id") String commentId,
			@RequestParam("video_id") String videoId,
			@RequestParam("author_id") String authorId,
			@RequestParam("reply_user") String replyUser,
			@RequestParam("comment_content")  String commentContent,
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestHeader(value = "Phone-Type", required = false) String phoneType
	) throws ServiceException, ReturnException {
		// todo commentContent not empty
		if(isForbidden(XThreadLocal.getInstance().getCurrentUser(), Constant.USER_AUTH_FORBID.PUBLISH_COMMENT.getIndex())){
			return error("5002");
		}

		if(!commentContent.equals(sensitiveService.filter(commentContent, 1))){
			return error("4002");
		}
		UserView user = getUserView(currentUser);

		CommentView commentView = new CommentView();
		commentView.setId(ObjectId.get().toString());
		commentView.setVid(videoId);
		commentView.setUid(currentUser);
		commentView.setAuthor(authorId);
		commentView.setReplyId(commentId);
		commentView.setReplyUser(replyUser);

		commentView.setCreatedAt(TimeUtil.getCurrentTimestamp());
		commentView.setDeleted(0);
		commentView.setActive(0);
		commentView.setContent(commentContent);
		commentView.setProfileImg(user.getProfileImg());
		commentView.setNickname(user.getNickname());
		commentView.setPhoneType(phoneType);
		commentService.saveComment(commentView);

		return success();

	}


	/**
	 * get all comments under current user's videos
	 *
	 * METHOD: GET
	 *
	 * @return
	 * 		state
	 * 		comments: map list
	 * 			comment: map
	 * 				comment_id
	 * 				video_id
	 * 				video_screenshot
	 * 				user_nickname
	 * 				user_avatar
	 * 				comment_time
	 * 				comment_content
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/user_comment")
	@ResponseBody
	@LoginRequired
	public Map<String, Object> userComment(
			@Validated PageForm pageForm,
			@RequestHeader("Weipai-Userid") String currentUser
			) throws ServiceException, ReturnException {
	    Map<String, Object> result = new HashMap<>();

		try {
			List<Map<String, String>> userCommentList = commentService.findUserCommentList(currentUser, ImmutableMap.of("deleted", "0"), ImmutableMap.of("uid", Constant.SYSTEM_USER.ID), pageForm.getCursor(), pageForm.getCount());
			result.put("comments", userCommentList);
			return success(result);
		} catch (TException e) {
			log.error(e.getMessage(), e);
			return error();
		}
	}
	
	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/user_reply")
	@ResponseBody
	@LoginRequired
	public Map<String, Object> userReply(
			@Validated PageForm pageForm,
			@RequestHeader("Weipai-Userid") String currentUserid
			) throws ServiceException, ReturnException {

		try {
			List<Map<String, String>> userReplyList = commentService.findUserReplyList(currentUserid, ImmutableMap.of("status", "0"), ImmutableMap.of("uid", Constant.SYSTEM_USER.ID), pageForm.getCursor(), pageForm.getCount());
			Map<String, Object> result = new HashMap<>();
			result.put("comments", userReplyList);
			return success(result);
		} catch (TException e) {
			log.error(e.getMessage(), e);
			return error();
		}


	}

	/**
	 *
	 * @return
	 * @throws ServiceException
	 */
	@RequestMapping(value = "/comment/delete")
	@ResponseBody
	@LoginRequired
	public Map<String, Object> commentDelete(
			@RequestParam("comment_id") String cid,
			@RequestParam("video_id") String vid,
			@RequestHeader("Weipai-Userid") String currentUser
	) throws ServiceException, ReturnException {
		int ret = commentService.deleteComment(vid, cid, currentUser);

		if (ret == 1) {
			return success();
		}
		else {
			return error();
		}
	}

}