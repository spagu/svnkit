/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.internal.wc.ISVNEventListener;
import org.tmatesoft.svn.core.internal.wc.SVNEvent;
import org.tmatesoft.svn.core.internal.wc.SVNEventAction;
import org.tmatesoft.svn.core.internal.wc.SVNEventStatus;
import org.tmatesoft.svn.core.internal.wc.SVNRevision;
import org.tmatesoft.svn.core.internal.wc.SVNUpdater;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class UpdateCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String path;
            path = getCommandLine().getPathAt(i);

            long revNumber = parseRevision(getCommandLine(), null, null);
            SVNRevision revision = SVNRevision.HEAD;
            if (revNumber >= 0) {
                revision = SVNRevision.create(revNumber);
            }
            SVNUpdater updater = new SVNUpdater(getCredentialsProvider(), new ISVNEventListener() {
                private boolean isExternal = false;
                private boolean isChanged = false;
                private boolean isExternalChanged = false;
                
                public void svnEvent(SVNEvent event) {
                    if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                        if (isExternal) {
                            isExternalChanged = true;
                        } else {
                            isChanged = true;
                        }
                        println(out, "A    " + getPath(event.getFile()));
                    } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
                        if (isExternal) {
                            isExternalChanged = true;
                        } else {
                            isChanged = true;
                        }
                        println(out, "D    " + getPath(event.getFile()));
                    } else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
                        StringBuffer sb = new StringBuffer();
                        if (event.getNodeKind() != SVNNodeKind.DIR) {
                            if (event.getContentsStatus() == SVNEventStatus.CHANGED) {
                                sb.append("U");
                            } else if (event.getContentsStatus() == SVNEventStatus.CONFLICTED) {
                                sb.append("C");
                            } else if (event.getContentsStatus() == SVNEventStatus.MERGED) {
                                sb.append("G");
                            } else {
                                sb.append(" ");
                            }
                        } else {
                            sb.append(' ');
                        }
                        if (event.getPropertiesStatus() == SVNEventStatus.CHANGED) {
                            sb.append("U");
                        } else if (event.getPropertiesStatus() == SVNEventStatus.CONFLICTED) {
                            sb.append("C");
                        } else if (event.getPropertiesStatus() == SVNEventStatus.CONFLICTED) {
                            sb.append("M");
                        } else {
                            sb.append(" ");
                        }
                        if (sb.toString().trim().length() != 0) {
                            if (isExternal) {
                                isExternalChanged = true;
                            } else {
                                isChanged = true;
                            }
                        }
                        if (event.getLockStatus() == SVNEventStatus.LOCK_UNLOCKED) {
                            sb.append("B");
                        } else {
                            sb.append(" ");
                        } 
                        println(out, sb.toString() + " " + getPath(event.getFile()));
                    } else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {                    
                        if (!isExternal) {
                            if (isChanged) {
                                println(out, "Updated to revision " + event.getRevision() + ".");
                            } else {
                                println(out, "At revision " + event.getRevision() + ".");
                            }
                        } else {
                            if (isExternalChanged) {
                                println(out, "Updated external to revision " + event.getRevision() + ".");
                            } else {
                                println(out, "External at revision " + event.getRevision() + ".");
                            }
                            println(out);
                            isExternalChanged = false;
                            isExternal = false;
                        }
                    } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
                        println(out);
                        println(out, "Updating external item at '" + event.getPath() + "'");
                        isExternal = true;
                    } else if (event.getAction() == SVNEventAction.RESTORE) {
                        println(out, "Restored '" + getPath(event.getFile()) + "'");
                    }
                }
            });
            
            File file = new File(path);
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (!parent.exists() || !SVNWCAccess.isVersionedDirectory(parent)) {
                    if (!getCommandLine().hasArgument(SVNArgument.QUIET)) {
                        println(out, "Skipped '" +  getPath(file).replace('/', File.separatorChar) + "'");
                    }
                    return;
                }
            }
            try {
                updater.doUpdate(file.getAbsoluteFile(), revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            } catch (Throwable th) {
                DebugLog.error(th);
                println(err, th.getMessage());
                println(err);
                error = true;
            }
        }    
        if (error) {
            System.exit(1);
        }
    }
}
