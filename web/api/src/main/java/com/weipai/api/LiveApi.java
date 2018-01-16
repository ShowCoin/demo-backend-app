package com.weipai.api;

import com.weipai.annotation.LoginRequired;
import com.weipai.application.LiveContributionDTO;
import com.weipai.application.LiveForecastCardDTO;
import com.weipai.application.LiveManagerDTO;
import com.weipai.application.LiveSquareDTO;
import com.weipai.application.LiveStopDTO;
import com.weipai.application.LiveStory;
import com.weipai.application.QCloudLiveNotificationFilter;
import com.weipai.application.UserCardDTO;
import com.weipai.application.VerificationStory;
import com.weipai.common.JacksonUtil;
import com.weipai.common.TimeUtil;
import com.weipai.common.Version;
import com.weipai.common.exception.ReturnException;
import com.weipai.domain.model.live.LiveManager;
import com.weipai.form.PageForm;
import com.weipai.infrastructure.live.qcloud.QCloudLiveNotification;
import com.weipai.infrastructure.live.qcloud.QCloudLiveStreamStopNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/live")
public class LiveApi extends BaseApi {

    private static final Logger logger = LoggerFactory.getLogger(LiveApi.class);

    private static final String ENTITY = "entity";

    private final LiveStory liveStory;
    private final LiveManager liveManager;
    private final VerificationStory verificationStory;

    @Autowired
    public LiveApi(LiveStory liveStory, LiveManager liveManager, VerificationStory verificationStory) {
        this.liveStory = liveStory;
        this.liveManager = liveManager;
        this.verificationStory = verificationStory;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map create(
            @RequestParam("title") String title,
            @RequestParam(value = "isGameLive", required = false, defaultValue = "0") int isGameLive,
            @RequestParam(value = "displayType", required = false, defaultValue = "0") int displayType,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws Exception {
        Map<String, Object> map = verificationStory.getVerificationMap(currentUser);
        map.putAll(liveStory.create(currentUser, title, isGameLive, displayType));

        return success(map);
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> getLivesAndForecasts(
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws Exception {
        List<LiveSquareDTO> lives = liveStory.list();
        // 查询直播预告列表信息(`status` = 1，按距离当前时间由近到远的顺序排序)
        List<LiveForecastCardDTO> forecasts = liveStory.findEnabledForecast(currentUser);
        Map<String, Object> map = new HashMap<>();
        map.put("list", lives);
        map.put("forecasts", forecasts);
        return success(map);
    }

    @RequestMapping(value = "/list_live_and_forecast", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> getListPushedAndForecast(
            @RequestHeader("Weipai-Userid") String currentUser
    ) {
        final Map<String, Object> map = liveStory.listPushedAndForecast(currentUser);
        return success(map);
    }

    @RequestMapping(value = "/enter_by_user_id", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map enterByUserId(
            @RequestParam(value = "id") String userId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        Map entity = liveStory.enterByUserId(userId, currentUser);
        Map<String, Object> map = new HashMap<>();
        map.put(ENTITY, entity);
        return success(map);
    }

    @RequestMapping(value = "/enter", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map enter(
            @RequestParam(value = "id") String liveId,
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestHeader("Channel") String channel
    ) throws ReturnException {
        Map entity = liveStory.enter(liveId, currentUser, channel);
        Map<String, Object> map = new HashMap<>();
        map.put(ENTITY, entity);
        return success(map);
    }

    @RequestMapping(value = "/leave", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map play(
            @RequestParam(value = "id") String liveId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) {
        liveStory.leave(liveId, currentUser);
        return success();
    }

    @RequestMapping(value = "/stop", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map stop(
            @RequestParam(value = "id") String liveId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        Map<String, Object> map = new HashMap<>();
        LiveStopDTO live = liveStory.stop(liveId, currentUser, false);
        map.put(ENTITY, live);
        return success(map);
    }

    @RequestMapping(value = "/like", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map like(
            @RequestParam(value = "id") String liveId,
            @RequestParam(value = "times") int times,
            @RequestHeader("Weipai-Userid") String currentUser
    ) {
        Map<String, Object> map = new HashMap<>();
        liveStory.like(liveId, currentUser, times);
        return success(map);
    }

    /**
     * 直播评论
     *
     * @param liveId
     * @param content
     * @param currentUser
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/comment", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map comment(
            @RequestParam(value = "id") String liveId,
            @RequestParam(value = "content") String content,
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestHeader("Client-Version") Version version,
            @RequestHeader("os") String os
    ) throws ReturnException {
        return success(liveStory.comment(liveId, currentUser, content, version, os));
    }

    @RequestMapping(value = "/paid_comment", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map paidComment(
            @RequestParam(value = "id") String liveId,
            @RequestParam(value = "content") String content,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        if (content == null || content.length() > 18) {
            return error("4005");
        }
        return success(liveStory.paidComment(liveId, currentUser, content));
    }

    /**
     * 直播分享
     *
     * @param liveId
     * @param platform
     * @param redPacketId
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/share", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map share(
            @RequestParam(value = "id") String liveId,
            @RequestParam(value = "platform") String platform,
            @RequestParam(value = "redPacketId", required = false, defaultValue = "0") int redPacketId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        liveStory.shareSuccess(liveId, currentUser, redPacketId, platform);
        return success();
    }

    /**
     * 直播送礼
     *
     * @param liveId
     * @param giftId
     * @param combo
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/send_gift", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map sendGift(
            @RequestParam(value = "live_id") String liveId,
            @RequestParam(value = "gift_id") int giftId,
            @RequestParam(value = "combo", defaultValue = "1") int combo,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        Map data = liveStory.sendGift(currentUser, liveId, giftId, combo);
        return success(data);
    }

    /**
     * 直播禁言
     *
     * @param liveId
     * @param userId
     * @param type
     * @param currentUser
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/block_user", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map blockUser(
            @RequestParam(value = "live_id") String liveId,
            @RequestParam(value = "user_id") String userId,
            @RequestParam(value = "type") String type,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        if ("add".equals(type))
            liveStory.blockUser(currentUser, liveId, userId);
        else
            liveStory.unblockUser(currentUser, liveId, userId);
        return success();
    }

    @RequestMapping(value = "/user_card", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map userCard(
            @RequestParam(value = "user_id") String userId,
            @RequestParam(value = "live_id") String liveId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        UserCardDTO userCardDTO = liveStory.userCard(currentUser, userId, liveId);
        Map<String, Object> map = new HashMap<>();
        map.put("entity", userCardDTO);
        return success(map);
    }

    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    @ResponseBody
    public String callback(
            @Validated @RequestBody QCloudLiveNotification qCloudLiveNotification
    ) throws IOException, ReturnException {
        logger.info("live callback : {}", JacksonUtil.writeToJsonString(qCloudLiveNotification));
        if (qCloudLiveNotification instanceof QCloudLiveStreamStopNotification) {
            logger.info("live callback inside: {}", JacksonUtil.writeToJsonString(qCloudLiveNotification));
            liveStory.streamStopNotify(qCloudLiveNotification.getStream_id());
        }
        return "{ \"code\":0 }";
    }

    @InitBinder("QCloudLiveNotification")
    protected void initBinder(WebDataBinder binder) {
        binder.addValidators(new QCloudLiveNotificationFilter(liveManager));
    }

    @RequestMapping(value = "/stat", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map stat(@RequestParam(value = "id") String liveId) throws ReturnException {
        Map<String, Object> map = new HashMap<>();
        LiveStopDTO live = liveStory.stat(liveId);
        map.put(ENTITY, live);
        return success(map);
    }

    /**
     * 主播（直播管理员）信息列表
     *
     * @param currentUser
     * @param role
     * @param pageForm
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/get_manager_list", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> getLiveManagerListByUserId(
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestParam("role") String role,
            @Validated PageForm pageForm)
            throws ReturnException {
        List<LiveManagerDTO> users = liveStory.getLiveManagerOrUserList(currentUser, pageForm.getCursor(), pageForm.getCount(), role);
        Map<String, Object> map = new HashMap<>();

        map.put("user", users);
        map.put("next_cursor", pageForm.getNextCursor(users));
        return success(map);
    }

    // 添加当前用户对应的直播管理员
    @RequestMapping(value = "/set_manager", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map setLiveManager(
            @RequestParam("manager_id") String managerId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {

        liveStory.setLiveManager(currentUser, managerId, TimeUtil.getCurrentTimestamp());

        return success();
    }

    // 删除当前用户对应的指定直播管理员
    @RequestMapping(value = "/delete_manager", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map deleteLiveManager(
            @RequestParam("manager_id") String managerId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {

        liveStory.deleteLiveManager(managerId, currentUser);

        return success();
    }

    /**
     * 每日、每周、每月及总贡献排行榜
     *
     * @param currentUser
     * @param contributionType
     * @param pageForm
     * @return
     * @throws ReturnException
     */
    @RequestMapping(value = "/get_live_contribution_list", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> getLiveContributionListOrderly(
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestParam("contribution_type") String contributionType,
            @Validated PageForm pageForm
    ) throws ReturnException {
        List<LiveContributionDTO> liveContributionList = liveStory.getLiveContributionListOrderly(currentUser, contributionType, pageForm.getCursor(), pageForm.getCount());

        Map<String, Object> map = new HashMap<>();

        map.put("users", liveContributionList);
        String next_cursor = pageForm.getNextCursor(liveContributionList);
        map.put("next_cursor", !next_cursor.isEmpty() && Integer.valueOf(next_cursor) < 80 ? next_cursor : "");
        return success(map);
    }

    /**
     * 在线直播收礼排行榜
     *
     * @param pageForm
     * @return
     */
    @RequestMapping(value = "/charts", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> findOnLiveCharts(
            @Validated PageForm pageForm
    ) throws ReturnException {
        List<LiveContributionDTO> liveContributionDTOs = liveStory.findOnLiveCharts(pageForm.getCursor(), pageForm.getCount());
        Map<String, Object> data = new HashMap<>();
        data.put("users", liveContributionDTOs);
        data.put("next_cursor", pageForm.getNextCursor(liveContributionDTOs));
        return success(data);
    }

    /**
     * 发起任务
     *
     * @param taskId
     * @param liveId
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/task/start", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> startTask(
            @RequestParam("live_id") String liveId,
            @RequestParam("task_id") Integer taskId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        Map<String, Object> data = liveStory.startTask(currentUser, liveId, taskId);
        return success(data);
    }

    /**
     * 任务处理
     *
     * @param decision
     * @param currentUser
     * @param taskTransactionId
     * @return
     */
    @RequestMapping(value = "/task/deal", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> dealTask(
            @RequestParam("decision") String decision,
            @RequestHeader("Weipai-Userid") String currentUser,
            @RequestParam("task_transaction_id") Integer taskTransactionId
    ) throws ReturnException {
        liveStory.dealTask(currentUser, taskTransactionId, decision);
        return success();
    }

    /**
     * 游戏开始
     *
     * @param liveId
     * @param gameId
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/game/start", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map gameStart(
            @RequestParam("live_id") String liveId,
            @RequestParam(value = "game_id", defaultValue = "0") Integer gameId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws Exception {
        liveStory.gameStart(currentUser, liveId, gameId);
        return success();
    }

    /**
     * 玩家下注
     *
     * @param gameLogId
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/game/stake", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map gameStake(
            @RequestParam("stake") Integer stake,
            @RequestParam("choice") Integer choice,
            @RequestParam("chatroom_id") String chatroomId,
            @RequestParam("game_log_id") Integer gameLogId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        return success(liveStory.gameStake(currentUser, stake, choice, gameLogId, chatroomId));
    }

    /**
     * 发起红包接口
     *
     * @param totalAccount
     * @param count
     * @param detail
     * @return
     */
    @RequestMapping(value = "/send_red_packet", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map sendRedPacket(
            @RequestParam("totalAccount") int totalAccount,
            @RequestParam("count") int count,//红包个数
            @RequestParam("detail") String detail,
            @RequestParam("liveId") String liveId,
            @RequestParam("chatroomId") String chatroomId,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        return success(liveStory.sendRedPacket(totalAccount, count, detail, liveId, chatroomId,
                currentUser));
    }

    @RequestMapping(value = "/open_red_packet", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map openRedPacket(
            @RequestParam("id") int id,
            @RequestParam("liveId") String liveId,
            @RequestHeader("Weipai-Userid") String receiver
    ) throws ReturnException {
        return success(liveStory.openRedPacket(id, liveId, receiver));
    }

    /**
     * 直播送礼（手绘礼物）
     *
     * @param liveId
     * @param drawGiftId
     * @param amount
     * @param path
     * @param duration
     * @param currentUser
     * @return
     */
    @RequestMapping(value = "/send_draw_gift", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map sendDrawGift(
            @RequestParam(value = "live_id") String liveId,
            @RequestParam(value = "draw_gift_id") int drawGiftId,
            @RequestParam(value = "amount") int amount,
            @RequestParam(value = "path") String path,
            @RequestParam(value = "duration") String duration,
            @RequestParam(value = "screenWidth") int screenWidth,
            @RequestHeader("Weipai-Userid") String currentUser
    ) throws ReturnException {
        return success(liveStory.sendDrawGift(currentUser, liveId, drawGiftId, amount, path, duration, screenWidth));
    }

    @RequestMapping(value = "/apply_mic", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map applyMic(@RequestParam(value = "liveId") String liveId,
                        @RequestHeader(value = "Weipai-Userid") String fromUserId,
                        @RequestParam(value = "toUserId") String toUserId) throws ReturnException {
        return success(liveStory.applyMic(liveId, fromUserId, toUserId));
    }


    @RequestMapping(value = "/doApply_mic", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map doReApplyMic(@RequestParam(value = "id") int id,
                            @RequestParam(value = "receiveType") String receiveType,
                            @RequestParam(value = "liveId") String liveId,
                            @RequestHeader(value = "Weipai-Userid") String currentUser) throws ReturnException {
        return success(liveStory.doApplyMic(id, receiveType, liveId, currentUser));
    }

    @RequestMapping(value = "/cancel_mic", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map cancelMic(@RequestParam("id") int id,
                         @RequestParam("liveId") String liveId,
                         @RequestParam("type") int type,
                         @RequestHeader("Weipai-Userid") String currentUser) throws ReturnException {
        return success(liveStory.cancelMic(id, liveId, type, currentUser));
    }


    @RequestMapping(value = "/game_list", method = RequestMethod.POST)
    @ResponseBody
    @LoginRequired
    public Map gameList() throws ReturnException {
        return success(liveStory.gameList());
    }

}
