/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.File;
import java.util.Date;
import java.util.List;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNBaseNode {

    private SVNWCDbStatus myStatus;
    private SVNWCDbKind myKind;
    private long myWCId;
    private long myReposId;
    private String myReposPath;
    private String myLocalRelativePath;
    private long myRevision;
    private SVNProperties myProps;
    private long myChangedRevision;
    private Date myChangedDate;
    private String myChangedAuthor;
    private SVNDepth myDepth;

    private List myChildren;
    private SVNChecksum myChecksum;
    private long myTranslatedSize;
    private String myTarget;

    public SVNBaseNode(SVNWCDbStatus status, SVNWCDbKind kind, long wcId, long reposId, String reposPath, String localRelativePath, 
            long revision, SVNProperties props, long changedRevision, Date changedDate, String changedAuthor, SVNDepth depth, List children, 
            SVNChecksum checksum, long translatedSize, String target) {
        myStatus = status;
        myKind = kind;
        myWCId = wcId;
        myReposId = reposId;
        myReposPath = reposPath;
        myLocalRelativePath = localRelativePath;
        myRevision = revision;
        myProps = props;
        myChangedRevision = changedRevision;
        myChangedDate = changedDate;
        myChangedAuthor = changedAuthor;
        myDepth = depth;
        myChildren = children;
        myChecksum = checksum;
        myTranslatedSize = translatedSize;
        myTarget = target;
    }
    
    public SVNWCDbStatus getStatus() {
        return myStatus;
    }
    
    public SVNWCDbKind getKind() {
        return myKind;
    }
    
    public long getWCId() {
        return myWCId;
    }
    
    public long getReposId() {
        return myReposId;
    }
    
    public String getReposPath() {
        return myReposPath;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public SVNProperties getProperties() {
        return myProps;
    }
    
    public long getChangedRevision() {
        return myChangedRevision;
    }
    
    public Date getChangedDate() {
        return myChangedDate;
    }
    
    public String getChangedAuthor() {
        return myChangedAuthor;
    }
    
    public List getChildren() {
        return myChildren;
    }
    
    public SVNChecksum getChecksum() {
        return myChecksum;
    }
    
    public long getTranslatedSize() {
        return myTranslatedSize;
    }
    
    public String getTarget() {
        return myTarget;
    }

    public String getLocalRelativePath() {
        return myLocalRelativePath;
    }
    
    public SVNDepth getDepth() {
        return myDepth;
    }
    
    public boolean hasChildren() {
        return myChildren != null && !myChildren.isEmpty();
    }
}