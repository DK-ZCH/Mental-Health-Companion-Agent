package com.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.annotation.IgnoreAuth;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.entity.SysConfigEntity;
import com.entity.EIException;
import com.service.SysConfigService;
import com.utils.R;

/**
 * 上传文件映射表
 */
@RestController
@RequestMapping("file")
@SuppressWarnings({"unchecked","rawtypes"})
public class FileController{
	@Autowired
    private SysConfigService configService;
	/**
	 * 上传文件
	 */
	@RequestMapping("/upload")
	public R upload(@RequestParam("file") MultipartFile file,String type) throws Exception {
		if (file.isEmpty()) {
			throw new EIException("上传文件不能为空");
		}
		String fileExt = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".")+1);
		File path = new File(ResourceUtils.getURL("classpath:static").getPath());
		if(!path.exists()) {
		    path = new File("");
		}
		File upload = new File(path.getAbsolutePath(),"/upload/");
		if(!upload.exists()) {
		    upload.mkdirs();
		}
		String fileName = new Date().getTime()+"."+fileExt;
		File dest = new File(upload.getAbsolutePath()+"/"+fileName);
		file.transferTo(dest);
		if(StringUtils.isNotBlank(type) && type.equals("1")) {
			SysConfigEntity configEntity = configService.getOne(new QueryWrapper<SysConfigEntity>().eq("config_key", "faceFile"));
			if(configEntity==null) {
				configEntity = new SysConfigEntity();
				configEntity.setConfigKey("faceFile");
				configEntity.setConfigValue(fileName);
			} else {
				configEntity.setConfigValue(fileName);
			}
			configService.saveOrUpdate(configEntity);
		}
		return R.ok().put("file", fileName);
	}
	
	/**
	 * 下载文件
	 */
	@IgnoreAuth
	@RequestMapping("/download")
	public ResponseEntity<byte[]> download(@RequestParam String fileName) {
		try {
			File path = new File(ResourceUtils.getURL("classpath:static").getPath());
			if(!path.exists()) {
			    path = new File("");
			}
			File upload = new File(path.getAbsolutePath(),"/upload/");
			if(!upload.exists()) {
			    upload.mkdirs();
			}
			// S2-A（A1）：路径边界约束 —— 核心是 canonical 包含校验，字符串过滤仅作辅助
			// 说明：成功仍为 201、未命中仍走兜底 500、不改 /file/upload —— 本批不扩大行为变化
			if (fileName == null || fileName.trim().isEmpty() || fileName.indexOf('\0') >= 0) {
				return new ResponseEntity<byte[]>(HttpStatus.FORBIDDEN);
			}
			File base = upload.getCanonicalFile();
			File file = new File(base, fileName).getCanonicalFile();
			// 规范化后必须位于基目录之下（含基目录本身），否则拒绝
			if (!file.getPath().equals(base.getPath())
					&& !file.getPath().startsWith(base.getPath() + File.separator)) {
				return new ResponseEntity<byte[]>(HttpStatus.FORBIDDEN);
			}
			if(file.exists()){
				/*if(!fileService.canRead(file, SessionManager.getSessionUser())){
					getResponse().sendError(403);
				}*/
				HttpHeaders headers = new HttpHeaders();
			    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);    
			    headers.setContentDispositionFormData("attachment", fileName);    
			    return new ResponseEntity<byte[]>(FileUtils.readFileToByteArray(file),headers, HttpStatus.CREATED);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ResponseEntity<byte[]>(HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
}
