package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.CommentDTO;
import com.weipai.application.PictureDTO;
import com.weipai.application.PictureStory;
import com.weipai.application.feed.FeedDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
@RequestMapping("/picture")
public class PictureApi extends BaseApi{
	private static final Logger log = LoggerFactory.getLogger(PictureApi.class);

	private final PictureStory pictureStory;

	@Autowired
	public PictureApi(PictureStory pictureStory) {
		this.pictureStory = pictureStory;
	}

	@RequestMapping(value = "/create", method = POST)
	@ResponseBody
	@LoginRequired
	public Map create(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "file_path") String filePath,
			@RequestParam(value = "description") String description,
			@RequestParam(value = "number") int number,
			@RequestParam(value = "city") String city,
			@RequestParam(value = "format", defaultValue = "jpg") String format
	) throws Exception {
		final FeedDTO feedDTO = pictureStory.create(currentUser, filePath, number, format, description, city);
		Map<String, Object> map = new HashMap<>();
	    map.put("picture", feedDTO);
	    return success(map);
	}

    /**
     * 图片送礼
     * @param pictureId
     * @param giftId
     * @param currentUser
     * @return
     * @throws Exception
     */
	@RequestMapping(value = "/send_gift", method = POST)
	@ResponseBody
	@LoginRequired
	public Map sendGift(
			@RequestParam(value = "picture_id") String pictureId,
			@RequestParam(value = "gift_id") int giftId,
			@RequestHeader("Weipai-Userid") String currentUser
	) throws Exception {
		Map<String, Object> data = pictureStory.sendGift(currentUser, pictureId, giftId);
		return success(data);
	}

	/**
	 * 评论列表
	 *
	 * @param pictureId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/comment/list", method = GET)
	@ResponseBody
	@LoginRequired
	public Map getComments(
			@RequestParam(value = "picture_id") String pictureId
	) throws Exception {
		List<CommentDTO> comments = pictureStory.getEnableComments(pictureId);

		Map<String, Object> map = new HashMap<>();
		map.put("comments", comments);
		return success(map);
	}

	/**
	 * 添加评论
	 *
	 * @param currentUser
	 * @param pictureId
	 * @param detail
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/comment/add", method = POST)
	@ResponseBody
	@LoginRequired
	public Map createComment(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "picture_id") String pictureId,
			@RequestParam(value = "detail") String detail
	) throws Exception {
		pictureStory.createComment(currentUser, pictureId, detail);
		return success();
	}

	/**
	 * 删除评论
	 *
	 * @param commentId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/comment/delete", method = POST)
	@ResponseBody
	@LoginRequired
	public Map deleteComment(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "comment_id") String commentId
	) throws Exception {
		pictureStory.deleteComment(currentUser, commentId);
		return success();
	}

	/**
	 * 点赞
	 *
	 * @param pictureId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/like", method = POST)
	@ResponseBody
	@LoginRequired
	public Map like(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "picture_id") String pictureId
	) throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put("like", pictureStory.like(currentUser, pictureId));
		return success(map);
	}

	/**
	 * 分享
	 *
	 * @param pictureId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/share", method = POST)
	@ResponseBody
	@LoginRequired
	public Map share(
			@RequestParam(value = "picture_id") String pictureId
	) throws Exception {
		pictureStory.share(pictureId);
		return success();
	}

	/**
	 * 关注详情
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/detail", method = GET)
	@ResponseBody
	@LoginRequired
	public Map getDetail(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "picture_id") String pictureId
	) throws Exception {
		PictureDTO picture = pictureStory.getDetailByPictureId(currentUser, pictureId);

		Map<String, Object> map = new HashMap<>();
		map.put("picture", picture);
		return success(map);
	}

	/**
	 * 删除图文
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/delete", method = POST)
	@ResponseBody
	@LoginRequired
	public Map deleteDetail(
			@RequestHeader("Weipai-Userid") String currentUser,
			@RequestParam(value = "picture_id") String pictureId
	) throws Exception {
		pictureStory.deletePictureBySelf(currentUser, pictureId);

		return success();
	}
}
