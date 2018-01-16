package com.weipai.api;

import com.codiform.moo.curry.Translate;
import com.google.common.collect.ImmutableMap;
import com.weipai.annotation.LoginRequired;
import com.weipai.application.GameDTO;
import com.weipai.application.ManageStory;
import com.weipai.application.TaskDTO;
import com.weipai.common.Constant.ADMIN_STATUS;
import com.weipai.common.Constant.REQUEST_AGENT_TYPE;
import com.weipai.common.Constant.USER_AGENT;
import com.weipai.common.JacksonUtil;
import com.weipai.common.TimeUtil;
import com.weipai.common.Version;
import com.weipai.common.cache.LocalCache;
import com.weipai.common.client.kafka.Producer;
import com.weipai.common.exception.ReturnException;
import com.weipai.common.exception.ServiceException;
import com.weipai.interceptor.XThreadLocal;
import com.weipai.manage.thrift.view.VersionControlView;
import com.weipai.pay.thrift.view.GiftListView;
import com.weipai.pay.thrift.view.GiftTypeView;
import com.weipai.pay.thrift.view.GiftView;
import com.weipai.pay.thrift.view.TaskTypeView;
import com.weipai.pay.thrift.view.TaskView;
import com.weipai.service.GiftService;
import com.weipai.service.ManageService;
import com.weipai.service.TaskService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.*;
import scala.collection.immutable.Stream;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.weipai.common.Event.E_CLIENT_VERSION_UPDATE;

@Controller
public class ManageApi extends BaseApi {
    private static final Logger log = LoggerFactory.getLogger(ManageApi.class);

    @Autowired
    private GiftService giftService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private ManageService manageService;
    @Autowired
    private ManageStory manageStory;

    @RequestMapping("/version_control")
    @ResponseBody
    public Map versionControl(HttpServletRequest request) throws ReturnException {
        String version = request.getHeader("Client-Version");
        String phoneType = request.getHeader("Phone-Type");
        String userId = request.getHeader("Weipai-Userid");
        String deviceUuid = request.getHeader("Device-Uuid");
        USER_AGENT userAgent = XThreadLocal.getInstance().getUserAgent();
        String os = userAgent.toOSString();
        Map<String, Object> resultMap = new HashMap<>();
        if (!StringUtils.isEmpty(version)) {
            int source = 0;
            if (os.indexOf("Android") != -1) {
                source = REQUEST_AGENT_TYPE.ANDROID.getCode();
            } else if (os.indexOf("IPhone") != -1) {
                source = REQUEST_AGENT_TYPE.IOS.getCode();
            }
            VersionControlView versionControl = null;
            try {
                versionControl = manageService.findVersionControlByAgentType(source);
            } catch (ServiceException e) {
                log.error(e.getMessage(), e);
            }
            int version_state = 0;
            if (versionControl != null) {
                if (isAndroid7(phoneType) && (version.equals("1.8.0") || version.equals("1.8.1"))) {
                    version_state = 3;
                } else if (version.compareTo(versionControl.getCoercionVersion()) < 0) {
                    //强制
                    version_state = 2;
                } else if (version.compareTo(versionControl.getCoercionVersion()) >= 0
                        && version.compareTo(versionControl.getAdviceVersion()) < 0) {
                    //建议
                    version_state = 1;
                } else {
                    //可用
                    version_state = 3;
                }
                resultMap.put("version_state", version_state);
                resultMap.put("new_kernel_version", versionControl.getAdviceVersion());
                resultMap.put("client_download_url", versionControl.getDownloadUrl());
                resultMap.put("update_title", versionControl.getTitle());
                resultMap.put("update_msg", versionControl.getMsg());


                String jackson = JacksonUtil.writeToJsonString(ImmutableMap.of(
                        "clientVersion", version,
                        "time", TimeUtil.getCurrentTimestamp(),
                        "deviceUuid", deviceUuid,
                        "userId", userId
                ));
                Producer.getInstance().sendData(E_CLIENT_VERSION_UPDATE.name(), jackson);


            } else {
                resultMap.put("version_state", "");
                resultMap.put("new_kernel_version", "");
                resultMap.put("client_download_url", "");
                resultMap.put("update_title", "");
                resultMap.put("update_msg", "");
            }
            resultMap.put("update_upgrade", "抢先体验");
            resultMap.put("update_ignore", "放弃机会");
            resultMap.put("update_cancel", "残忍拒绝");
        } else {
            return error("2019");
        }
        return success(resultMap);
    }


    /**
     * 礼物列表
     *
     * @return
     */
    @RequestMapping("/gift_list")
    @ResponseBody
    public Map giftList() throws ReturnException {

        List<GiftTypeView> giftTypeList = null;
        try {
            giftTypeList = new LocalCache<List<GiftTypeView>>() {

                @Override
                public List<GiftTypeView> getAliveObject() throws Exception {
                    return giftService.getGiftTypeList(ADMIN_STATUS.ENABLE);
                }
            }.put(60 * 30, "gift_type_list_" + ADMIN_STATUS.ENABLE);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return error();
        }

        Map<String, Object> resultMap = new HashMap<>();
        List giftList = new ArrayList();

        if (giftTypeList != null) {

            for (final GiftTypeView giftTypeView : giftTypeList) {

                Map<String, Object> giftTypeMap = new HashMap<>();
                giftTypeMap.put("class", giftTypeView.getTypeName());
                GiftListView giftListView = null;
                try {
                    giftListView = new LocalCache<GiftListView>() {

                        @Override
                        public GiftListView getAliveObject() throws Exception {
                            return giftService.getGiftList(giftTypeView.getTypeId(), ADMIN_STATUS.ENABLE, 0, Integer.MAX_VALUE);
                        }
                    }.put(60 * 10, "gift_list_" + ADMIN_STATUS.ENABLE + "_" + giftTypeView.getTypeId() + "_all");

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

                List giftsList = new ArrayList();
                if (giftListView != null && giftListView.giftList != null) {

                    for (GiftView giftView : giftListView.giftList) {
                        Map<String, Object> gift = new HashMap<String, Object>();
                        gift.put("price", giftView.getPrice());
                        gift.put("experience", giftView.getPrice());
                        gift.put("value", giftView.getPrice() == 0 ? 1 : giftView.getPrice());
                        gift.put("descript", giftView.getGiftName());
                        gift.put("tag", "");
                        gift.put("image", giftView.getPicture());
                        gift.put("gid", giftView.getGiftId());
                        giftsList.add(gift);

                    }
                }

                giftTypeMap.put("gifts", giftsList);
                giftList.add(giftTypeMap);
            }
        } else {
            return error();
        }

        resultMap.put("gift_list", giftList);
        resultMap.put("version", 150);
        return success(resultMap);

    }

    /**
     * 礼物列表（新）
     *
     * @param type
     * @return
     */
    @RequestMapping("/gift/list")
    @ResponseBody
    @LoginRequired
    public Map getGifts(@RequestParam("type") String type,
                        @RequestHeader("os") String osType,
                        @RequestHeader("Client-Version") Version version) throws ReturnException {
        return success(manageStory.findGifts(type, osType, version));
    }

    /**
     * 任务列表
     *
     * @return
     */
    @RequestMapping(value = "/task/list", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map<String, Object> findTasks() throws ReturnException {
        List<Map<String, Object>> list = findTasksGroups();
        Map<String, Object> map = new HashMap<>();
        map.put("list", list);
        return success(map);
    }

    private List<Map<String, Object>> findTasksGroups() throws ReturnException {
        try {
            // 缓存所有有效任务类型信息(降序)
            final List<TaskTypeView> taskTypeViews = findEnableTaskTypesOrderly();
            // 缓存所有类目任务信息
            final List<TaskView> taskViews = new LocalCache<List<TaskView>>() {
                @Override
                public List<TaskView> getAliveObject() throws Exception {
                    // 查询所有有效任务信息（降序）
                    Map<String, String> equalConditions = new HashMap<>();
                    equalConditions.put("status", String.valueOf(ADMIN_STATUS.ENABLE.ordinal()));
                    return taskService.findTasks(equalConditions, "sort_value", 0, Integer.MAX_VALUE);
                }
            }.put(60 * 10, "tasksGroups_" + ADMIN_STATUS.ENABLE + "_all");
            // 将所有有效任务按任务类型有序进行分组，每组容量为随机5项任务，将新分组组成列表
            return divideTasksIntoGroups(taskTypeViews, taskViews);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ReturnException("0");
        }
    }

    // 将所有有效任务按有序任务类型进行分组，将新分组组成列表（降序）
    private List<Map<String, Object>> divideTasksIntoGroups(List<TaskTypeView> taskTypeViews, List<TaskView> taskViews) {
        List<Map<String, Object>> tasksGroups = new ArrayList<>();
        if (taskTypeViews == null || taskViews == null) {
            return tasksGroups;
        }
        for (TaskTypeView taskTypeView : taskTypeViews) {
            Map<String, Object> tasksGroup = new HashMap<>();
            List<TaskDTO> taskDTOs = buildRandomTaskDTOs(findTaskDTOsByTypeId(taskViews, taskTypeView.getTypeId()), 5);
            if (taskDTOs.size() > 0) {
                tasksGroup.put("class", taskTypeView.getTypeName());
                tasksGroup.put("tasks", taskDTOs);
                tasksGroups.add(tasksGroup);
            }
        }
        return tasksGroups;
    }

    // 将Task列表按照类型抽取出来
    private List<TaskDTO> findTaskDTOsByTypeId(List<TaskView> taskViews, Integer typeId) {
        List<TaskDTO> taskDTOs = new ArrayList<>();
        for (TaskView taskView : taskViews) {
            if (taskView.getTypeId() != typeId) {
                continue;
            }

            taskDTOs.add(Translate.to(TaskDTO.class).from(taskView));
        }
        return taskDTOs;
    }

    // 提取指定数量的Task随机列表
    private List<TaskDTO> buildRandomTaskDTOs(List<TaskDTO> taskDTOs, Integer size) {
        if (taskDTOs.size() > size) {
            Collections.shuffle(taskDTOs);
            return taskDTOs.subList(0, size);
        } else {
            return taskDTOs;
        }

    }

    // 缓存所有有效任务类型信息(降序)
    private List<TaskTypeView> findEnableTaskTypesOrderly() throws ReturnException {
        try {
            return new LocalCache<List<TaskTypeView>>() {
                @Override
                public List<TaskTypeView> getAliveObject() throws Exception {
                    List<TaskTypeView> taskTypeViews = taskService.findTaskTypes(
                            ImmutableMap.of("status", String.valueOf(ADMIN_STATUS.ENABLE.ordinal())),
                            "sort_value", 0, Integer.MAX_VALUE);
                    return taskTypeViews != null ? taskTypeViews : new ArrayList<TaskTypeView>();
                }
            }.put(60 * 60, "taskTypes_" + ADMIN_STATUS.ENABLE + "_all");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ReturnException("0");
        }
    }

    /**
     * 判断字符串是否是安卓7，如果是返回true,否则返回false
     */

    private boolean isAndroid7(String phoneType) {
        if (null == phoneType) {
            return false;
        }
        if (phoneType.contains("android")) {
            int lastIndexOf = phoneType.lastIndexOf("_");
            String androidVersion = phoneType.substring(lastIndexOf + 1, phoneType.length());
            //androidVersion第一个字符就是系统号
            int index = androidVersion.indexOf(".");
            String number = androidVersion.substring(0, index);
            if (number.equals("7")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 游戏列表
     *
     * @return
     */
    @RequestMapping("/game/list")
    @ResponseBody
    @LoginRequired
    public Map findGames() throws ReturnException {
        List<GameDTO> list = manageStory.findGames();

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("list", list);
        return success(resultMap);
    }

    @RequestMapping(value = "/config/alert", method = RequestMethod.GET)
    @ResponseBody
    @LoginRequired
    public Map configAlert(@RequestHeader("Weipai-Userid") String currentUser) {
        return success(manageStory.configAlert(currentUser));
    }

    @RequestMapping(value = "/private_letter/check",method = RequestMethod.POST)
    @LoginRequired
    @ResponseBody
    public Map privateLetterCheck(@RequestHeader("Weipai-Userid")String currentUser,
                                  @RequestParam("toUserId") String toUserId) throws Exception{
        return manageStory.checkPrivateLetter(currentUser,toUserId);
    }
}
