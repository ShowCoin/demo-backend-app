package com.weipai.api;

import com.google.common.collect.ImmutableMap;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.cache.RemoteCache;
import com.weipai.common.exception.AuthException;
import com.weipai.common.exception.ReturnException;
import com.weipai.form.PageFormFilter;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.service.*;
import com.weipai.service.impl.thirdparty.WeiboService;
import com.weipai.stat.thrift.view.UserStatView;
import com.weipai.struc.HeaderParams;
import com.weipai.user.thrift.view.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJacksonJsonView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseApi implements HandlerExceptionResolver {

    private static final Map<String, String> reasons = ImmutableMap.<String, String>builder()
            .put("0", "未知错误")
            .put("1000", "参数不足")
            .put("1001", "尚未支持")
            .put("1002", "参数错误")
            .put("1003", "请升级到最新版本")
            .put("1004", "您访问的频道不存在")

            //用户相关
            .put("2001", "该昵称已被他人使用")
            .put("2002", "该账号还没有注册过")
            .put("2003", "密码错误")
            .put("2004", "该账号已经被注册了")
            .put("2005", "未找到该用户")
            .put("2006", "已绑定其他帐号")
            .put("2007", "昵称包含不支持的字符")
            .put("2008", "昵称长度不能超过8个汉字")
            .put("2009", "账号格式不正确")
            .put("2010", "与绑定的第三方帐号不一致")
            .put("2011", "密码长度应为6-18位")
            .put("2012", "第三方验证错误")
            .put("2013", "手机号格式错误")
            .put("2014", "手机号码验证错误或者已经失效")
            .put("2015", "该手机号码已经绑定其他账号，确定解除绑定之前的账号吗？")
            .put("2016", "网络繁忙，请重新登陆。")
            .put("2017", "本设备已经注册成功，请返回并直接登录。请勿在短时间内，同一设备反复注册，多谢您的支持。")
            .put("2018", "无法发送验证码，因为发送次数已经达到限制。")
            .put("2019", "登录已过期，请重新登录")
            .put("2020", "请勿在短时间内反复注册，多谢您的支持。")
            .put("2021", "暂不支持直接注册，请使用第三方账号注册登录")
            .put("2022", "绑定两个以上第三方账号才能使用此功能")
            .put("2023", "您已注册过该账号")
            .put("2024", "尚未支持该第三方账号登录")
            .put("2025", "该账号已经注册过")
            .put("2026", "昵称太火，请换一个昵称再试")
            .put("2027", "操作太过频繁，请休息一下再尝试")

            //关系相关
            .put("2100", "不能关注自己")
            .put("2101", "你已经关注过了")
            .put("2102", "你还没有关注人家呢")
            .put("2103", "关注超出限制数量")

            //图片相关
            .put("3001", "图片不能为空")
            .put("3002", "图片文件尺寸过大")
            .put("3003", "不支持的文件格式")
            .put("3004", "上传失败")

            //评论相关
            .put("4001", "评论不能重复")
            .put("4002", "评论含有敏感词,请重新输入！")
            .put("4003", "短时间内不能重复发表评论")
            .put("4004", "只有会员或是赠送小礼物之后才可以评论哦")
            .put("4005", "评论超过字数限制")

            //权限相关
            .put("5001", "你已被对方禁止此操作")
            .put("5002", "抱歉，你被禁止此操作")
            .put("5003", "抱歉，你无权进行操作")
            .put("5004", "你发的太快啦，冲杯咖啡吧，稍后再来~")
            .put("5005", "验证过期，请重新登陆")
            .put("5006", "会员能看到更多视频哦~")
            .put("5007", "您已经被禁止登陆，如有疑问请联系客服")
            .put("5008", "抱歉，由于之前的视频内容违规，你的视频权限被小秘书取消了，具体请联系微拍小秘书")
            .put("5009", "您已经被禁止直播，如有疑问请联系客服")
            .put("5010", "微拍官方账号不能被禁言")
            .put("5011", "您已被管理员禁言")
            .put("5012", "您的发言不合法")
            .put("5013", "您的发言过于频繁，休息一下再发吧")
            .put("5014", "只有主播才可以禁言60级以上用户哦")
            .put("5016","微拍会员,资费10元/月(不含通信费),确认购买成功后可享受所有服务")

            //私信相关
            .put("6001", "你打招呼太频繁了，休息一下再发吧")
            .put("6002", "你们已经是好友了，去对话页面聊天吧")
            .put("6003", "<a href=\"hi://#uid#\">打个招呼，说句话</a>，你还不是对方好友。")
            .put("6010", "获取token失败")
            .put("6011", "创建聊天室失败")
            .put("6012", "用户加入聊天室失败")
            .put("6013", "销毁聊天室失败")
            .put("6014", "发送消息失败")
            .put("6015", "禁言失败")
            .put("6016", "取消禁言失败")

            //视频相关
            .put("7001", "该视频已经被删除")
            .put("7002", "您无权删除该视频")
            .put("7003", "该视频正在审核中")
            .put("7004", "该视频已经无法查看")

            //付费相关
            .put("8001", "赠送一次爱心需要花费100个钻石，会员可以赠送大量钻石哟，现在钻石余额不足，请到会员中心充值")
            .put("8002", "充值失败")
            .put("8003", "https needed")
            .put("8004", "invalid")
            .put("8005", "交易失败")
            .put("8006", "礼物不存在")
            .put("8007", "生成预订单失败")
            .put("8008", "送礼失败")
            .put("8009", "提现金额必须为整千")
            .put("8010", "提现金额超过拥有的微币")
            .put("8011", "最小提现金额为10000钻石")
            .put("8012", "每日免费观看次数已用完。播放一次视频花费10个钻石。现在钻石余额不足，请到会员中心充值。")
            .put("8013", "余额不足，请到会员中心充值")
            .put("8014", "您是公会成员，无法提现。请联系您所属的公会咨询相关事宜")
            .put("8015", "您未通过实名认证不能提现哦")

            //地理位置相关
            .put("9001", "没有获得位置信息，试试打开gps吧~")
            .put("9002", "不支持的城市")


            //评论相关
            .put("10001", "您无权限删除该条评论")
            .put("10002","十级以下用户,每天只能和五个人发私信")
            .put("10003","黑名单用户不能发私信")
            .put("10004","您发言内容涉及敏感内容")

            //安全相关
            .put("11001", "请求头参数错误")
            .put("11002", "签名错误")
            .put("11003", "已满每日最多验证次数")
            .put("11004", "获取过于频繁")
            .put("11005", "您的手机时间不准确，请调整")

            // light
            .put("12001", "重复的订单id")
            .put("12002", "没有权限")
            // live
            .put("13001", "直播已经结束")
            .put("13002", "直播创建关闭")
            .put("13003", "您已被禁言")

            // 图文相关
            .put("14001", "该图文已经被删除")

            // 任务相关
            .put("15001", "主播任务进行中......")
            .put("15002", "已完成任务")
            .put("15003", "已拒绝任务")
            .put("15004", "操作已超时")

            // 骰子游戏
            .put("16001", "游游戏进行中，请稍后再试哦")
            .put("16002", "该场游戏没有赢家和输家")
            .put("16003", "下注筹码超出范围")
            .put("16004", "游戏已结束")
            .put("16005", "只允许下注一次")
            .put("16006", "下注失败......")
            //红包
            .put("17001", "红包数额要在100-10000之间哦")
            .put("17002", "一次至少要发一个红包，最多只能发100红包哦")
            .put("17004", "您分享以后才能领红包哦")
            .put("17005", "您已经领过红包了")

            //实名认证
            .put("18001", "您未通过实名认证不能开直播哦")
            .put("18002", "您输入的手机号码不合法")
            .put("18003", "您输入的身份证号码不合法")
            .put("18004", "您已提交认证，请等待审核")

            // 手绘礼物
            .put("19001", "手绘数量超出范围")

            // 连麦
            .put("20001", "主播正在连麦中，请稍后再试")
            .put("20002", "对方溜号了")
            .put("20003", "签约主播或单次直播收益达到10万微钻，即可连麦哦")
            .put("20004", "40级以上或当场直播榜三且最低1万微钻，才可连麦哦")
            .put("20005", "对方拒绝了您的连麦请求")

            // 活动相关
            .put("21001", "您的能量豆不足")
            .put("21002", "能量豆赠送失败")
            .build();


    @Autowired
    protected UserService userService;
    @Autowired
    protected StatService statService;
    @Autowired
    protected RelationService relationService;
    @Autowired
    protected VerifyCodeService verifyCodeService;
    @Autowired
    protected VideoService videoService;
    @Autowired
    protected WeiboService weiboService;
    @Autowired
    protected GpsService gpsService;
    @Autowired
    protected ManageService manageService;

    @Autowired
    PageFormFilter pageFormFilter;

    private static final Logger log = LoggerFactory.getLogger(BaseApi.class);

    public Map getReturnJson() {
        return returnJson;
    }

    protected Map returnJson = null;

    /**
     * 请求成功
     *
     * @param data : 要返回的数据
     * @return : 加上成功状态返回
     * <p>
     * 例子 :
     * Map<String, String> result = new HashMap<>();
     * return ReturnHandle.success(result);
     */
    public static Map success(Map data) {
        Map result = new HashMap(data);
        result.put("state", "1");
        return result;
    }

    public static Map success(List data) {
        Map<String, Object> map = new HashMap<>();
        map.put("state", "1");
        map.put("data", data);
        return map;
    }

    /**
     * 请求成功
     *
     * @return : 加上成功状态返回
     * <p>
     * 例子 :
     * Map<String, String> result = new HashMap<>();
     * return ReturnHandle.success(result);
     */
    public static Map success() {
        return success(new HashMap<String, String>());
    }

    /**
     * 请求失败
     *
     * @param code : 失败的code
     * @return 例子 :
     * return error("1001");
     */
    private static Map _error(String code) {
        Map result = new HashMap<String, Object>();
        result.put("state", code);
        String reason = reasons.get(code);
        result.put("reason", reason);
        log.error("response error. code : {}, reason : {}", code, reason);
        return result;
    }

    public static Map error(String code) throws ReturnException {
        throw new ReturnException(code);
    }

    public static Map error() throws ReturnException {
        return error("0");
    }

    protected List<Integer> getForbiddenList(final String uid) {

        try {
            List<Integer> forbiddenList = new LocalCache<List<Integer>>() {
                @Override
                public List<Integer> getAliveObject() throws Exception {
                    return userService.getForbiddenActionListByUid(uid);
                }

            }.put(60 * 10, "user_forbidden_list_" + uid);

            return forbiddenList;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;

    }

    protected UserView getUserView(final String uid) {
        if (uid == null) {
            return null;
        }

        UserView user = null;

        try {
            user = new LocalCache<UserView>() {

                @Override
                public UserView getAliveObject() throws Exception {
                    return new RemoteCache<UserView>() {

                        @Override
                        public UserView getAliveObject() throws Exception {
                            return userService.findUserById(uid);
                        }
                    }.put(60 * 10, "user:" + uid);

                }
            }.put(60 * 10, "user:" + uid);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }
        return user;
    }

    protected UserView getUserView() {
        String uid = XThreadLocal.getInstance().getCurrentUser();
        return getUserView(uid);
    }

    protected UserStatView getUserStatView(final String uid) {
        if (uid == null) {
            return null;
        }

        UserStatView userStat = null;

        try {
            userStat = new LocalCache<UserStatView>() {

                @Override
                public UserStatView getAliveObject() throws Exception {
                    return statService.findUserStatByUid(uid);
                }
            }.put(10, "user_stat:" + uid);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
        }

        if (userStat == null) {
            userStat = new UserStatView();
            userStat.setUid(uid);
        }

        return userStat;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception exception) {
        log.error(exception.getMessage() + "[" + httpServletRequest.getRequestURI() + "]", exception);
        HeaderParams headerParams = XThreadLocal.getInstance().getHeaderParams();
        if (("ios".equals(headerParams.getOs()) && "6.1.0".compareTo(headerParams.getClientVersion()) <= 0)
                || ("android".equals(headerParams.getOs()) && "2.0.0".compareTo(headerParams.getClientVersion()) <= 0)) {
            httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        }

        if (exception instanceof MaxUploadSizeExceededException) {
            return new ModelAndView(new MappingJacksonJsonView(), _error("3002"));
        } else if (exception instanceof MissingServletRequestParameterException) {
            return new ModelAndView(new MappingJacksonJsonView(), _error("1000"));
        } else if (exception instanceof ReturnException) {
            return new ModelAndView(new MappingJacksonJsonView(), _error(exception.getMessage()));
        } else if (exception instanceof AuthException) {
            return new ModelAndView(new MappingJacksonJsonView(), _error(exception.getMessage()));
        } else if (exception instanceof SecurityException) {
            return new ModelAndView(new MappingJacksonJsonView(), _error(exception.getMessage()));
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return new ModelAndView(new MappingJacksonJsonView(), _error("0"));
        }


    }

    /**
     * 判断字符串中全部为数字
     *
     * @param str
     * @return
     */
    public static boolean isVideoId(String str) {
        return str.matches("^?(([0-9a-f]+))$");
    }

    protected boolean isForbidden(String uid, int action) {
        boolean flag = false;
        try {
            flag = userService.userIsForbidden(uid, action);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return flag;
    }

    @InitBinder("PageForm")
    protected void initPageFormBinder(WebDataBinder binder) {
        binder.addValidators(pageFormFilter);
    }

    @InitBinder("VideoPageForm")
    protected void initVideoPageFormBinder(WebDataBinder binder) {
        binder.addValidators(pageFormFilter);
    }

    @InitBinder("NewVideoPageForm")
    protected void initNewVideoPageFormBinder(WebDataBinder binder) {
        binder.addValidators(pageFormFilter);
    }


    public static void main(String[] args) {
        System.out.println(isVideoId("7BEA7020-D4E1-42CB-B3B0-AAFF9C7384BE"));
    }

}
