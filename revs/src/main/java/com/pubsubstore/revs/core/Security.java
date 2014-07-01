package com.pubsubstore.revs.core;

import java.io.Serializable;
import java.util.Map;

public class Security implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2591957614189730707L;
	
	private String userId;
	private String password;
	private Map<String, String> roleMap;
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public Map<String, String> getRoleMap() {
		return roleMap;
	}
	public void setRoleMap(Map<String, String> roleMap) {
		this.roleMap = roleMap;
	}
	
	

}
