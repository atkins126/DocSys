package com.DocSystem.common.remoteStorage;

import com.DocSystem.common.Log;
import com.jcraft.jsch.*;
import com.jcraft.jsch.ChannelSftp.LsEntry;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
 
public class SFTPUtil {
 
    //server host and port
    private String host;
    private int port; 
    //userName and pwd for login with userName
    private String username;
    private String password;
    //private key path for login with ssh
    private String privateKey;
 
    private ChannelSftp sftp;
    private Session session;
 
    /**
     * 构造基于密码认证的sftp对象
     */
    public SFTPUtil(String username, String password, String host, int port) {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
    }
 
    /**
     * 构造基于秘钥认证的sftp对象
     */
    public SFTPUtil(String username, String host, int port, String privateKey) {
        this.username = username;
        this.host = host;
        this.port = port;
        this.privateKey = privateKey;
    }
 
    public SFTPUtil() {
    }
 
    public boolean login() {
    	boolean ret = false;
    	try {
            JSch jsch = new JSch();
            if (privateKey != null) {
                jsch.addIdentity(privateKey);// 设置私钥
            }
            
            Log.debug("login host:" + host + " port:" + port + " userName:" + username + " password:" + password);
            session = jsch.getSession(username, host, port);
 
            if (password != null) {
                session.setPassword(password);
            }
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            
            //解决Kerberos username
            config.put("PreferredAuthentications","publickey,keyboard-interactive,password"); 

            session.setConfig(config);
            session.connect();
 
            Channel channel = session.openChannel("sftp");
            channel.connect();
 
            sftp = (ChannelSftp) channel;
            ret = true;
        } catch (JSchException e) {
            e.printStackTrace();
        }
    	return ret;
    }
 
    /**
     * 关闭连接 server
     */
    public void logout() {
        if (sftp != null) {
            if (sftp.isConnected()) {
                sftp.disconnect();
            }
        }
        if (session != null) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }
 
    //获取文件列表
    public Vector<?> listFiles(String directory) throws SftpException {
    	return sftp.ls(directory);
    }
    
    //上传文件
    public boolean upload(String directory, String sftpFileName, InputStream input)
    {
    	boolean ret = false;
        try {
            sftp.cd(directory);
            sftp.put(input, sftpFileName);  //上传文件
            ret = true;
        } catch (Exception e) {
        	e.printStackTrace();
        }
        return ret;
    }
    
    //下载文件
    public boolean download(String remotePath, String localPath, String fileName) {
        System.out.println("download remotePath:" + remotePath + " localPath:" + localPath + " fileName:" + fileName);
        boolean ret = false;
        File file = null;
        FileOutputStream os = null;
        
        try {
            sftp.cd(remotePath);
            File localDir = new File(localPath);
            if(localDir.exists() == false)
            {
            	localDir.mkdirs();
            }
            file = new File(localPath + fileName);
            os = new FileOutputStream(file);
            sftp.get(remotePath + fileName, os);
            ret = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        	if(os != null)
        	{
        		try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
        	}
        }
        return ret;
    }
 
    //新增目录
    public boolean mkdir(String directory) throws SftpException {
    	boolean ret = false;
    	try {
            sftp.mkdir(directory);
            ret =  true;
        } catch (SftpException e) {
        	e.printStackTrace();
        }
    	return ret;
    }
    
    //删除操作
    public boolean delete(String directory, String fileName)
    {
    	boolean ret = false;
        try {
			//sftp.cd(directory);
	        //sftp.rm(fileName);
	        sftp.rm(directory + fileName);
	        ret = true;
        } catch (Exception e) {
			Log.debug("delete() delete file [" + directory + fileName + "] Failed");
			e.printStackTrace();
		}
        return ret;
    }
    
    public boolean delDirs(String directory, String fileName) throws SftpException {
    	boolean ret = false;
		try {       	        	
			Vector<?> list = sftp.ls(directory + fileName);
			if(list != null)
			{
				for(int i=0; i<list.size(); i++)
				{
					LsEntry subEntry = (LsEntry) list.get(i);
					String subEntryName = subEntry.getFilename();
					if(subEntryName.equals(".") || subEntryName.equals(".."))
					{
						continue;
					}
					
					if(subEntry.getAttrs().isDir())
					{
						if(delDirs(directory + fileName + "/", subEntryName) == false)
						{
							Log.debug("delDirs() delete folder [" + directory + fileName + subEntryName + "] Failed");
							return false;
						}
					}
					else
					{
						if(delFile(directory + fileName + "/", subEntryName) == false)
						{
							return false;
						}
					}
				}
			}
			ret = delDir(directory, fileName);
		} catch (SftpException e) {
			e.printStackTrace();
		}
    	return ret;
    }
    
    public boolean delDir(String directory, String fileName) throws SftpException {
    	boolean ret = false;
    	try {
            sftp.rmdir(directory + fileName);
            ret =  true;
        } catch (SftpException e) {
			Log.debug("delDir() delete file [" + directory + fileName + "] Failed");
        	e.printStackTrace();
        }
    	return ret;
    }

    public boolean delFile(String directory, String fileName) throws SftpException {
        boolean ret = false;
    	try {
            sftp.rm(directory + fileName);
            ret =  true;
        } catch (SftpException e) {
			Log.debug("delFile() delete file [" + directory + fileName + "] Failed");
        	e.printStackTrace();
        }
    	return ret;
    }    

    //移动或重命名
    public boolean copy(String srcRemotePath, String srcName, String dstRemotePath, String dstName, boolean isMove, Integer type) throws SftpException {
       if(isMove)
       {
    	   return move(srcRemotePath, srcName, dstRemotePath, dstName);
       }
       
       return copy(srcRemotePath, srcName, dstRemotePath, dstName, type);
    }  
    
	public boolean copy(String srcRemotePath, String srcName, String dstRemotePath, String dstName, Integer type) {
    	boolean ret = false;    	
    	if(type == 1)
    	{
    		Log.debug("copy() " + srcRemotePath + srcName + " is file");
			ret = copyFile(srcRemotePath, srcName, dstRemotePath, dstName);
    	}
    	else
    	{
    		Log.debug("copy() " + srcRemotePath + srcName + " is folder");
    		ret = copyDir(srcRemotePath, srcName, dstRemotePath, dstName);
    	}
    	return ret;
	}    
	
    private boolean copyDir(String srcRemotePath, String srcName, String dstRemotePath, String dstName) {
    	boolean ret = false;
		try
    	{	
			ret = mkdir(dstRemotePath + dstName);        	
			Vector<?> list = sftp.ls(srcRemotePath + srcName);
			if(list != null)
			{
            	if(ret == true)
            	{
	            	for(int i=0; i < list.size(); i++)
		            {
	            		LsEntry subEntry = (LsEntry) list.get(i);
	            		Log.debug("copy() subEntry:" + subEntry.getFilename());
	            		if(subEntry.getAttrs().isDir() == false)
		            	{
		            		copyFile(srcRemotePath + srcName + "/", subEntry.getFilename(), dstRemotePath + dstName + "/", subEntry.getFilename());
		            	}
		            	else
		            	{
		            		copyDir(srcRemotePath + srcName + "/", subEntry.getFilename(),  dstRemotePath + dstName + "/", subEntry.getFilename());
		            	}
	    			}
            	}
			}
        } catch (Exception e) {
			e.printStackTrace();
		}
        return ret;
	} 
	
	public boolean copyFile(String srcRemotePath, String srcName, String dstRemotePath, String dstName) {
    	boolean ret = false;
        try {
        	InputStream fos = sftp.get(srcRemotePath + srcName);
        	sftp.put(fos, dstRemotePath + dstName);
	        fos.close();
            ret = true;
        } catch (Exception e) {
			e.printStackTrace();
		}
        return ret;
	} 
    
    public boolean move(String srcRemotePath, String srcName, String dstRemotePath, String dstName) throws SftpException {
        boolean ret = false;
    	try {
            sftp.rename(srcRemotePath + srcName, dstRemotePath + dstName);
            ret =  true;
        } catch (SftpException e) {
        	e.printStackTrace();
        }
        return ret;        
    } 

    //切换目录
    public void cd(String directory){
        try {
			sftp.cd(directory);
		} catch (SftpException e) {
			e.printStackTrace();
		}
    }

 
    public boolean isDirExists(String directory) { 
        boolean ret = false;
    	try {
			sftp.cd(directory);
			ret = true;
        } catch (SftpException e) {
			e.printStackTrace();
		}
        return ret;
    }
 
    public boolean isFileExists(String directory, String fileName) {
 
        List<String> findFilelist = new ArrayList<String>();
        ChannelSftp.LsEntrySelector selector = new ChannelSftp.LsEntrySelector() {
            @Override
            public int select(ChannelSftp.LsEntry lsEntry) {
                if (lsEntry.getFilename().equals(fileName)) {
                    findFilelist.add(fileName);
                }
                return 0;
            }
        };
 
        try {
            sftp.ls(directory, selector);
        } catch (SftpException e) {
            e.printStackTrace();
        }
 
        if (findFilelist.size() > 0) {
            return true;
        } else {
            return false;
        }
    }
}
