/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess2 implements ISVNEventHandler {
    
    public static final int INFINITE_DEPTH = -1;
    
    private ISVNEventHandler myEventHandler;
    private ISVNOptions myOptions;
    private Map myAdminAreas;

    public static SVNWCAccess2 newInstance(ISVNEventHandler eventHandler) {
        return new SVNWCAccess2(eventHandler);
    }
    
    private SVNWCAccess2(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public void setEventHandler(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public ISVNEventHandler getEventHandler() {
        return myEventHandler;
    }
    
    public void checkCancelled() throws SVNCancelException {
        if (myEventHandler != null) {
            myEventHandler.checkCancelled();
        }
    }

    public void handleEvent(SVNEvent event) {
        handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    public void handleEvent(SVNEvent event, double progress) {
        if (myEventHandler != null) {
            try {
                myEventHandler.handleEvent(event, progress);
            } catch (Throwable th) {
            }
        }
    }

    public void setOptions(ISVNOptions options) {
        myOptions = options;
    }

    public ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = new DefaultSVNOptions();
        }
        return myOptions;
    }

    public SVNAdminAreaInfo openAnchor(File path, boolean writeLock, int depth) throws SVNException {
        File parent = path.getParentFile();
        String name = path.getName();
        SVNAdminArea parentArea = null;
        SVNAdminArea targetArea = null; 
        SVNException parentError = null;
        try {
            parentArea = open(parent, writeLock, 0);
        } catch (SVNException svne) {
            if (writeLock && svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                try {
                    parentArea = open(parent, false, 0);
                } catch (SVNException svne2) {
                    throw svne;
                }
                parentError = svne;
            } else {
                throw svne;
            }
        }
        
        try {
            targetArea = open(path, writeLock, depth);
        } catch (SVNException svne) {
            if (parentArea == null || svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
        }
        
        if (parentArea != null && targetArea != null) {
            SVNEntry2 parentEntry = null;
            SVNEntry2 targetEntry = null;
            SVNEntry2 targetInParent = null;
            try {
                targetInParent = parentArea.getEntry(name, false);
                targetEntry = targetArea.getEntry(targetArea.getThisDirName(), false);
                parentEntry = parentArea.getEntry(parentArea.getThisDirName(), false);
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            
            SVNURL parentURL = parentEntry.getSVNURL();
            SVNURL targetURL = targetEntry.getSVNURL();
            String encodedName = SVNEncodingUtil.uriEncode(name);
            if (targetInParent == null || (parentURL != null && targetURL != null && 
                    (!parentURL.equals(targetURL.removePathTail()) || !encodedName.equals(SVNPathUtil.tail(targetURL.getPath()))))) {
                if (myAdminAreas != null) {
                    myAdminAreas.remove(parent);
                }
                try {
                    doClose(parentArea, false);
                } catch (SVNException svne) {
                    try {
                        close();
                    } catch (SVNException svne2) {
                        //
                    }
                    throw svne;
                }
                parentArea = null;
            }
        }
        
        if (parentArea != null) {
            if (parentError != null && targetArea != null) {
                try {
                    close();
                } catch (SVNException svne) {
                    //
                }
                throw parentError;
            }
        }

        if (targetArea == null) {
            SVNEntry2 targetEntry = null;
            try {
                targetEntry = parentArea.getEntry(name, false); 
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            if (targetEntry != null && targetEntry.isDirectory()) {
                if (myAdminAreas != null) {
                    myAdminAreas.put(path, SVNAdminArea.MISSING);
                }
            }
        }
        SVNAdminArea anchor = parentArea != null ? parentArea : targetArea;
        SVNAdminArea target = targetArea != null ? targetArea : parentArea;
        return new SVNAdminAreaInfo(this, anchor, target, parentArea == null ? "" : name);
    }
    
    public SVNAdminArea open(File path, boolean writeLock, int depth) throws SVNException {
        Map tmp = new HashMap();
        SVNAdminArea area = doOpen(path, writeLock, depth, tmp);
        for(Iterator paths = tmp.keySet().iterator(); paths.hasNext();) {
            Object childPath = paths.next();
            SVNAdminArea childArea = (SVNAdminArea) tmp.get(childPath);
            myAdminAreas.put(childPath, childArea);
        }
        return area;
    }
    
    public SVNAdminArea probeOpen(File path, boolean writeLock, int depth) throws SVNException {
        File dir = probe(path);
        if (!path.equals(dir)) {
            depth = 0;
        }
        SVNAdminArea adminArea = null;
        try {
            adminArea = open(dir, writeLock, depth);
        } catch (SVNException svne) {
            SVNFileType childKind = SVNFileType.getType(path);
            SVNErrorCode errCode = svne.getErrorMessage().getErrorCode(); 
            if (!path.equals(dir) && childKind == SVNFileType.DIRECTORY && errCode == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err);
            } else {
                throw svne;
            }
        }
        return adminArea;
    }
    
    public void close() throws SVNException {
        if (myAdminAreas != null) {
            doClose(myAdminAreas, false);
            myAdminAreas.clear();
        }
    }
    
    public void closeAdminArea(File path) throws SVNException {
        if (myAdminAreas != null) {
            SVNAdminArea area = (SVNAdminArea) myAdminAreas.get(path);
            if (area != null) {
                doClose(area, false);
                myAdminAreas.remove(path);
            }
        }
    }
    
    private SVNAdminArea doOpen(File path, boolean writeLock, int depth, Map tmp) throws SVNException {
        // no support for 'under consturction here' - it will go to adminAreaFactory.
        tmp = tmp == null ? new HashMap() : tmp; 
        if (myAdminAreas != null) {
            SVNAdminArea existing = (SVNAdminArea) myAdminAreas.get(path);
            if (existing != null && existing != SVNAdminArea.MISSING) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(error);
            }
        } else {
            myAdminAreas = new HashMap();
        }
        
        SVNAdminArea area = SVNAdminAreaFactory.open(path);
        area.setWCAccess(this);
        
        if (writeLock) {
            area.lock();
            area = SVNAdminAreaFactory.upgrade(area);
        }
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            for(Iterator entries = area.entries(false); entries.hasNext();) {
                try {
                    checkCancelled(); 
                } catch (SVNCancelException e) {
                    doClose(tmp, false);
                    throw e;
                }
                
                SVNEntry2 entry = (SVNEntry2) entries.next();
                if (entry.getKind() != SVNNodeKind.DIR  || area.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                File childPath = new File(path, entry.getName());
                try {
                    // this method will put created area into our map.
                    doOpen(childPath, writeLock, depth, tmp);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        doClose(tmp, false);
                        throw e;
                    }
                    tmp.put(childPath, SVNAdminArea.MISSING);
                    continue;
                }
            }
        }
        tmp.put(path, area);
        return area;
    }
    
    private void doClose(Map adminAreas, boolean preserveLocks) throws SVNException {
        for (Iterator paths = adminAreas.keySet().iterator(); paths.hasNext();) {
            File path = (File) paths.next();
            SVNAdminArea adminArea = (SVNAdminArea) adminAreas.get(path);
            if (adminArea == SVNAdminArea.MISSING) {
                paths.remove();
                continue;
            }
            doClose(adminArea, preserveLocks);
            paths.remove();
        }
    }

    private void doClose(SVNAdminArea adminArea, boolean preserveLocks) throws SVNException {
        if (adminArea == SVNAdminArea.MISSING) {
            return;
        }
        if (!preserveLocks && adminArea.isLocked()) {
            adminArea.unlock();
        }
    }

    public SVNAdminArea probeRetrieve(File path) throws SVNException {
        File dir = probe(path);
        return retrieve(dir);
    }
    
    public boolean isMissing(File path) {
        if (myAdminAreas != null) {
            SVNAdminArea area = (SVNAdminArea) myAdminAreas.get(path);
            return area == SVNAdminArea.MISSING;
        }
        return false;
    }
    
    public boolean isWCRoot(File path) throws SVNException {
        SVNEntry2 entry = getEntry(path, false);
        SVNAdminArea parentArea = getAdminArea(path.getParentFile());
        if (parentArea == null) {
            try {
                parentArea = probeOpen(path.getParentFile(), false, 0);
            } catch (SVNException svne) {
                return true;
            }
        }
        
        SVNEntry2 parentEntry = getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            return true;
        }
        
        if (parentEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no ancestry information", path.getParentFile());
            SVNErrorManager.error(err);
        }
        
        if (entry != null && entry.getURL() != null) {
            if (!entry.getURL().equals(SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(path.getName())))) {
                return true;
            }
        }
        entry = getEntry(path, false);
        if (entry == null) {
            return true;
        }
        return false;
    }
    
    public SVNEntry2 getEntry(File path, boolean showHidden) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        String entryName = null;
        if (adminArea == null) {
            adminArea = getAdminArea(path.getParentFile());
            entryName = path.getName();
        } else {
            entryName = adminArea.getThisDirName();
        }
        
        if (adminArea != null) {
            return adminArea.getEntry(entryName, showHidden);
        }
        return null;
    }
    
    public void setRepositoryRoot(File path, SVNURL reposRoot) throws SVNException {
        SVNEntry2 entry = getEntry(path, false);
        if (entry == null) {
            return;
        }
        SVNAdminArea adminArea = null;
        String name = null;
        if (entry.isFile()) {
            adminArea = getAdminArea(path.getParentFile());
            name = path.getName();
        } else {
            adminArea = getAdminArea(path);
            name = adminArea != null ? adminArea.getThisDirName() : null;
        }
        
        if (adminArea == null) {
            return;
        }
        if (adminArea.tweakEntry(name, null, reposRoot.toString(), -1, false)) {
            adminArea.saveEntries(false);
        }
    }
    
    public SVNAdminArea retrieve(File path) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        if (adminArea == null) {
            SVNEntry2 subEntry = null;
            try {
                SVNAdminArea dirAdminArea = getAdminArea(path.getParentFile());
                if (dirAdminArea != null) {
                    subEntry = dirAdminArea.getEntry(path.getName(), true);
                }
            } catch (SVNException svne) {
                subEntry = null;
            }
            SVNFileType type = SVNFileType.getType(path);
            if (subEntry != null) {
                if (subEntry.getKind() == SVNNodeKind.DIR && type == SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a directory but found a file", path);
                    SVNErrorManager.error(err);
                } else if (subEntry.getKind() == SVNNodeKind.FILE && type == SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a file but found a directory", path);
                    SVNErrorManager.error(err);
                }
            }
            File adminDir = new File(path, SVNFileUtil.getAdminDirectoryName());
            SVNFileType wcType = SVNFileType.getType(adminDir);
            
            if (type == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' is missing", path);
                SVNErrorManager.error(err);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' containing working copy admin area is missing", adminDir);
                SVNErrorManager.error(err);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Unable to lock ''{0}''", path);
                SVNErrorManager.error(err);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is not locked", path);
            SVNErrorManager.error(err);
        }
        return adminArea;
    }

    //analogous to retrieve_internal
    private SVNAdminArea getAdminArea(File path) {
        //internal retrieve
        SVNAdminArea adminArea = null; 
        if (myAdminAreas != null) {
            adminArea = (SVNAdminArea) myAdminAreas.get(path);
        }
        if (adminArea == SVNAdminArea.MISSING) {
            adminArea = null;
        }
        return adminArea;
    }
    
    private File probe(File path) throws SVNException {
        int wcFormat = -1;
        SVNFileType type = SVNFileType.getType(path);
        if (type == SVNFileType.DIRECTORY) {
            wcFormat = SVNAdminAreaFactory.checkWC(path);
        } else {
            wcFormat = 0;
        }
        
        //non wc
        if (type != SVNFileType.DIRECTORY || wcFormat == 0) {
            if ("..".equals(path.getName()) || ".".equals(path.getName())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_PATH, "Path ''{0}'' ends in ''{1}'', which is unsupported for this operation", new Object[]{path, path.getName()});
                SVNErrorManager.error(err);
            }
            path = path.getParentFile();
        } 
        return path;
    }
}
