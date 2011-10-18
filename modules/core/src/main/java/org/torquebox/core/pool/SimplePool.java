/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.torquebox.core.pool;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.logging.Logger;

public class SimplePool<T> implements ManageablePool<T> {

    private String name;
    private final Set<T> instances = new HashSet<T>();
    private final Set<T> borrowedInstances = new HashSet<T>();
    private final Set<T> availableInstances = new HashSet<T>();

    private final PoolListeners<T> listeners = new PoolListeners<T>();
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }

    public void addListener(PoolListener<T> listener) {
        this.listeners.getListeners().add( listener );
    }

    public boolean removeListener(PoolListener<T> listener) {
        return this.listeners.getListeners().remove( listener );
    }

    @Override
    public T borrowInstance(String requester) throws Exception {
        return borrowInstance( requester, 0 );
    }

    public synchronized T borrowInstance(String requester, long timeout) throws InterruptedException {
        log.info(  "Borrow runtime requested by " + requester );
        long start = System.currentTimeMillis();
        this.listeners.instanceRequested( instances.size(), availableInstances.size() );
        while (availableInstances.isEmpty()) {
            long remainingTime = ((timeout == 0) ? 0 : (timeout - (System.currentTimeMillis() - start)));
            if ((timeout != 0) && (remainingTime <= 0)) {
                return null;
            }
            wait( remainingTime );
        }

        Iterator<T> iter = this.availableInstances.iterator();
        T instance = iter.next();
        
        long elapsed = System.currentTimeMillis() - start;
        log.info(  "Borrowed runtime by " + requester + " fullfilled in " + elapsed + "ms" );
        iter.remove();

        this.borrowedInstances.add( instance );

        this.listeners.instanceBorrowed( instance, instances.size(), availableInstances.size() );
        return instance;
    }

    @Override
    public synchronized void releaseInstance(T instance) {
        this.borrowedInstances.remove( instance );
        this.availableInstances.add( instance );
        this.listeners.instanceReleased( instance, instances.size(), availableInstances.size() );
        notifyAll();
    }

    public synchronized void fillInstance(T instance) {
        this.instances.add( instance );
        this.availableInstances.add( instance );
        notifyAll();
    }

    public T drainInstance() throws Exception {
        return drainInstance( 0 );
    }

    public synchronized T drainInstance(long timeout) throws Exception {
        T instance = borrowInstance( "anonymous-drain", timeout );
        this.borrowedInstances.remove( instance );
        this.instances.remove( instance );
        return instance;
    }
    
    Set<T> getAllInstances() {
        Set<T> instances = new HashSet<T>();
        instances.addAll( this.instances );
        return instances;
    }
    
    Set<T> getBorrowedInstances() {
        Set<T> instances = new HashSet<T>();
        instances.addAll( this.borrowedInstances);
        return instances;
    }
    
    Set<T> getAvailableInstances() {
        Set<T> instances = new HashSet<T>();
        instances.addAll( this.availableInstances );
        return instances;
    }

    synchronized int size() {
        return this.instances.size();
    }

    synchronized int borrowedSize() {
        return this.borrowedInstances.size();
    }

    synchronized int availableSize() {
        return this.availableInstances.size();
    }
    
    private static final Logger log = Logger.getLogger( "org.torquebox.core.pool" );

}
