package org.sakaiproject.scorm.content.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.thread_local.cover.ThreadLocalManager;

public class FastZipCHH extends ZipCHH {
	protected static final String VIRTUAL_FS_CACHE_KEY = "scormCHHVirtualFileSystem@";
	
	private static Log log = LogFactory.getLog(FastZipCHH.class);
	
	protected int countChildren(ContentEntity parent, int depth) {
		ContentResource realParent = (ContentResource) getRealParent(parent.getId());
		String relativePath = getRelativePath(realParent.getId(), parent.getId());
		
		VirtualFileSystem fs = getVirtualFileSystem(realParent);
		
		return fs.getCount(relativePath);
	}
	
	protected List<ContentEntity> extractChildren(ContentEntity parent, int depth) {
		ContentResource realParent = (ContentResource) getRealParent(parent.getId());
		String realParentId = realParent.getId();
		String relativePath = getRelativePath(realParent.getId(), parent.getId());
		
		VirtualFileSystem fs = getVirtualFileSystem(realParent);
		
		List<ContentEntity> list = new LinkedList<ContentEntity>();
		
		List<String> entityNames = fs.getChildren(relativePath);
		
		for (String name : entityNames) {
			if (name.endsWith(Entity.SEPARATOR))
				list.add(makeCollection(realParent, newId(relativePath, name), name));
			else
				list.add(makeResource(realParent, newId(relativePath, name), name, null));
		}

		return list;
	}
	
	
	protected VirtualFileSystem getVirtualFileSystem(ContentEntity parent) {
		ContentResource realParent = (ContentResource) getRealParent(parent.getId());
		String realParentId = realParent.getId();
		
		VirtualFileSystem fs = uncacheVirtualFileSystem(realParentId);
		if (fs == null) {
			fs = buildVirtualFileSystem(parent, realParent);
			if (fs != null)
				cacheVirtualFileSystem(realParentId, fs);
		}
		return fs;
	}
	
	protected VirtualFileSystem uncacheVirtualFileSystem(String key) {
		VirtualFileSystem fs = null;
		try {
			fs = (VirtualFileSystem) ThreadLocalManager.get(VIRTUAL_FS_CACHE_KEY + key);
		} catch(ClassCastException e) {
			log.error("Caught a class cast exception finding virtual file system with key " + key, e);
		}
		
		return fs;
	}
	
	protected void cacheVirtualFileSystem(String key, VirtualFileSystem fs) {
		if (null != fs) {			
			ThreadLocalManager.set(VIRTUAL_FS_CACHE_KEY + key, fs);
		}
	}
	
	protected VirtualFileSystem buildVirtualFileSystem(ContentEntity parent, ContentResource realParent) {
		final VirtualFileSystem fs = new VirtualFileSystem(realParent.getId());
		
		byte[] archive = null;
		
		try {
			if (null != realParent)
				archive = realParent.getContent();
		} catch (ServerOverloadException soe) {
			log.error("Caught a server overload exception trying to grab real parent's content", soe);
		}
		
		if (archive == null || archive.length <= 0)
			return null;
		
		final String relativePath = getRelativePath(realParent.getId(), parent.getId());
		
		ZipReader reader = new ZipReader(new ByteArrayInputStream(archive)) {
			
			protected boolean includeContent(boolean isDirectory) {
				return false;
			}
			
			protected boolean isValid(String entryPath) {
				/*if (entryPath.endsWith(Entity.SEPARATOR) && entryPath.length() > 1)
					entryPath = entryPath.substring(0, entryPath.length() - 1);
	    		
		    	return isDeepEnough(entryPath, relativePath, 1);*/
				return true;
			}
			
			protected Object processEntry(String entryPath, ByteArrayOutputStream outStream, boolean isDirectory) {			
				if (isDirectory && !entryPath.endsWith(Entity.SEPARATOR))
					entryPath += Entity.SEPARATOR;
				fs.addPath(entryPath);
				return null;
			}
			
		};
		
		reader.read();
		
		
		return fs;
	}
	
	
}
