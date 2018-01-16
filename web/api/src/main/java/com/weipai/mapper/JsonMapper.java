/**
 * 
 */
package com.weipai.mapper;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.StdSerializerProvider;

import java.io.IOException;

/**
 *
 */
public class JsonMapper extends ObjectMapper {
	
	public JsonMapper(){
		super();  
        // 允许单引号  
//        this.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);  
        // 字段和值都加引号  
//        this.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);  
        // 数字也加引号  
//        this.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);  
//        this.configure(JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS, true); 
        // 空值处理为空串  
		StdSerializerProvider sp = (StdSerializerProvider) this.getSerializerProvider();
        sp.setNullValueSerializer(new JsonSerializer<Object>(){

            @Override
            public void serialize(Object value, JsonGenerator jg,
                    SerializerProvider sp) throws IOException,
                    JsonProcessingException {
                 jg.writeString("");  
            }
             
        });
        
   
	}

}

