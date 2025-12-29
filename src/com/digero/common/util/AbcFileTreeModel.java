package com.digero.common.util;

import com.digero.common.view.UIText;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

// Implements a file tree for JTree, but the nodes directly
// under the dummy root node are a list of directories.
// Used for abcplayer playlist

public class AbcFileTreeModel implements TreeModel {
	
	public enum SortType {
		NAME_ASC, NAME_DESC, LAST_MODIFIED_ASC, LAST_MODIFIED_DESC, SIZE_ASC, SIZE_DESC;
		
		// Convert LAST_MODIFIED_DESC into Last Modified (desc)
		@Override
		public String toString() {
			String[] parts = super.toString().toLowerCase().split("_");
			for (int i = 0; i < parts.length - 1; i++) {
				parts[i] = parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1);
			}
			parts[parts.length - 1] = "(" + parts[parts.length - 1] + ")";
			return String.join(" ", parts);
		}
	}

	private ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
	private AbcSongFileNode rootNode;
	private static ExtensionFileFilter abcFilter = new ExtensionFileFilter(UIText.get("common.abc.files.and.playlists"),
			Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT, Util.ABCP_FILE_EXTENSION_NO_DOT); 
	
	public AbcFileTreeModel(List<File> directories) {
		this.rootNode = new AbcSongFileNode(new File("a_d7mmy_file-name_thatwillnever-9eused"));
		setDirectories(directories);
	}
	
	public void refresh(SortType sort) {
		for (AbcSongFileNode node : rootNode.children) {
			node.refresh(getComparator(sort));
		}
		
		for (TreeModelListener l : listeners) {
			l.treeStructureChanged(new TreeModelEvent(this, new TreePath(rootNode)));
		}
	}
	
	public void sort(SortType sort) {
		for (AbcSongFileNode node : rootNode.children) {
			node.sort(getComparator(sort));
		}
		
		for (TreeModelListener l : listeners) {
			l.treeStructureChanged(new TreeModelEvent(this, new TreePath(rootNode)));
		}
	}
	
	public void filter(String filterStr) {
		rootNode.filter(filterStr.toLowerCase());
		
		for (TreeModelListener l : listeners) {
			l.treeStructureChanged(new TreeModelEvent(this, new TreePath(rootNode)));
		}
	}
	
	public void setDirectories(List<File> directories) {
		rootNode.children.clear();
		for (File file : directories) {
			rootNode.children.add(new AbcSongFileNode(file));
		}
	}
	
	@Override
	public void addTreeModelListener(TreeModelListener arg0) {
		listeners.add(arg0);
	}

	@Override
	public Object getChild(Object parentObj, int index) {
		return ((AbcSongFileNode) parentObj).getChildAt(index);
	}

	@Override
	public int getChildCount(Object parentObj) {
		return ((AbcSongFileNode) parentObj).filteredChildren.size();
	}

	@Override
	public int getIndexOfChild(Object parentObj, Object childObj) {
		AbcSongFileNode parent = ((AbcSongFileNode)parentObj);
		AbcSongFileNode child = ((AbcSongFileNode)childObj);
		
		return parent.getIndexOf(child);
	}

	@Override
	public Object getRoot() {
		return rootNode;
	}

	@Override
	public boolean isLeaf(Object file) {
		AbcSongFileNode f = (AbcSongFileNode)file;
		return f != rootNode && f.getFile().isFile();
	}

	@Override
	public void removeTreeModelListener(TreeModelListener arg0) {
		listeners.remove(arg0);
	}

	@Override
	public void valueForPathChanged(TreePath arg0, Object arg1) {
		
	}
	
	public static Comparator<File> getFileComparator(SortType type) {
		Comparator<File> folderFirstComparator = (f1, f2) -> {
			if (f1.isDirectory() == f2.isDirectory()) {
				return 0;
			} else {
				return f1.isDirectory() ? -1 : 1;
			}
		};
		
		Comparator<File> sortComparator;
		
		switch (type) {
		case NAME_ASC:
			sortComparator = (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
			break;
		case NAME_DESC:
			sortComparator = (f1, f2) -> f2.getName().compareToIgnoreCase(f1.getName());
			break;
		case LAST_MODIFIED_ASC:
			sortComparator = (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified());
			break;
		case LAST_MODIFIED_DESC:
			sortComparator = (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified());
			break;
		case SIZE_ASC:
			sortComparator = (f1, f2) -> Long.compare(f1.length(), f2.length());
			break;
		case SIZE_DESC:
			sortComparator = (f1, f2) -> Long.compare(f2.length(), f1.length());
			break;
		default:
			sortComparator = (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
			break;
		}
		
		return folderFirstComparator.thenComparing(sortComparator);
	}
	
	public static Comparator<AbcSongFileNode> getComparator(SortType type) {
		Comparator<File> fileComparator = getFileComparator(type);
		
		return (f1, f2) -> fileComparator.compare(f1.getFile(), f2.getFile());
	}
	
	public class AbcSongFileNode {
		private final File theFile;
		private ArrayList<AbcSongFileNode> children = new ArrayList<AbcSongFileNode>();
		private ArrayList<AbcSongFileNode> filteredChildren = new ArrayList<AbcSongFileNode>();
		
		public void refresh(Comparator<AbcSongFileNode> sorter) {
			children.clear();
			if (!theFile.isDirectory() || !theFile.exists()) {
				return;
			}
			
			File[] childFiles = theFile.listFiles(abcFilter);
			
			if (childFiles == null) {
				return;
			}
			
			for (File file : childFiles) {
				AbcSongFileNode node = new AbcSongFileNode(file);
				node.refresh(sorter);
				children.add(node);
			}
			
			children.sort(sorter);
		}
		
		public void sort(Comparator<AbcSongFileNode> sorter) {
			for (AbcSongFileNode child : children) {
				child.sort(sorter);
			}
			
			children.sort(sorter);
		}
		
		public boolean filter(String filterStr) {
			boolean hasMatchedChild = false;
			
			filteredChildren.clear();
			
			for (AbcSongFileNode child : children)
			{
				if (child.filter(filterStr)) {
					hasMatchedChild = true;
					filteredChildren.add(child);
				}
			}
			
			return hasMatchedChild || theFile.getName().toLowerCase().contains(filterStr);
		}
		
		public AbcSongFileNode(final File theFile) {
			this.theFile = theFile;
		}
		
		public boolean isLeaf() {
			return theFile.isFile() || filteredChildren.isEmpty();
		}
		
		public AbcSongFileNode getChildAt(int i) {
			if (i < 0 || i >= filteredChildren.size()) {
				return null;
			}
			return filteredChildren.get(i);
		}
		
		public int getChildrenCount() {
			return filteredChildren.size();
		}
		
		public int getIndexOf(AbcSongFileNode node) {
			return filteredChildren.indexOf(node);
		}

	    public File getFile() {
	        return theFile;
	    }

	    @Override public String toString() {
	        return theFile.getName();
	    }
	    
	    @Override public boolean equals(Object o) {
	        return o instanceof AbcSongFileNode && ((AbcSongFileNode)o).theFile.equals(theFile);
	    }
	}

}
