package com.weipai.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;

import com.weipai.common.AreaNetCache;
import com.weipai.common.IPSeeker;
import com.weipai.init.WPInit;
import com.weipai.util.IpComparator;

public class TestIp {
	private IPSeeker ipsk;
	public TestIp(){
		ipsk = new IPSeeker();
	}
	public void iptest(){
		String net_area = ipsk.getIpgeter().search("111.128.19.78");
		if (net_area != null) {

			String[] net_area_s = net_area.split(",");
			int net = Integer.valueOf(net_area_s[0]);
			int area = Integer.valueOf(net_area_s[1]);

			String shi = WPInit.areaCache.get(area);
			String sheng = "";
			if (area >= 100) {
				sheng = WPInit.areaCache.get(area / 100);
			} else {
				sheng = shi;
			}
			String wangluo = WPInit.netCache.get(net);

			StringBuilder str_rs = new StringBuilder();
			str_rs.append("SourceIP：");
			str_rs.append("111.128.19.78");
			str_rs.append(" --> NettypeID，AredID = ");
			str_rs.append(net);
			str_rs.append("，");
			str_rs.append(area);
			str_rs.append("  [");
			str_rs.append(sheng);
			str_rs.append("：");
			str_rs.append(shi);
			str_rs.append("，");
			str_rs.append(wangluo);
			str_rs.append("]");
			System.out.println(str_rs);
		}
	}
	public static void main(String[] args) {
//		Map<String,Object> map = new HashMap<String,Object>();
//		IpComparator ipc = new IpComparator();
//		List<ConcurrentHashMap<String, Object>> cityList = (List<ConcurrentHashMap<String, Object>>)AreaNetCache.city.get("cityList");
//		Collections.sort(cityList,ipc);
//		map.put("cityList",cityList);
//		System.out.println(map);
		
	}
}
