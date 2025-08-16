package com.digero.maestro.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;

import javax.swing.DefaultListModel;

public class ListModelWrapper<E> extends AbstractList<E> {
	private final DefaultListModel<E> listModel;

	public ListModelWrapper(DefaultListModel<E> listModel) {
		this.listModel = listModel;
	}

	public DefaultListModel<E> getListModel() {
		return listModel;
	}

	@Override
	public void clear() {
		listModel.clear();
	}

	@Override
	public E get(int index) {
		return listModel.getElementAt(index);
	}

	@Override
	public int size() {
		return listModel.getSize();
	}

	@Override
	public E set(int index, E element) {
		return listModel.set(index, element);
	}

	@Override
	public void add(int index, E element) {
		listModel.add(index, element);
	}

	@Override
	public E remove(int index) {
		return listModel.remove(index);
	}

	@Override
	public boolean remove(Object o) {
		return listModel.removeElement(o);
	}

    /**
    * This could take 1.6 seconds for 24 part song.
    * With this method override it's now about 100 ms.
    * Listener were reason.
    */
    @Override
    public void sort(Comparator<? super E> c) {
        ArrayList<E> temp = new ArrayList<>(this);// 5 us
        temp.sort(c);// 8 us
        listModel.clear();// 19 ms
        listModel.addAll(temp);// 85 ms (1 listener)
    }
}