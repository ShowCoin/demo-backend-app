package com.weipai.api;

import com.weipai.common.exception.ServiceException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 */
@Controller
public class TestApi extends BaseApi{


    /**
     *
     * @return
     * @throws ServiceException
     */
    @RequestMapping(value = "/hello_world")
    @ResponseBody
    public Map hello_world() throws Exception {
        Map<String, Object> result = new HashMap<>();
        throw new Exception();
    }
}
