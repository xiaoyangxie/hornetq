/*
* JBoss, Home of Professional Open Source.
* Copyright 2010, Red Hat, Inc., and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.hornetq.api.core;


/**
 * An error has happened at HornetQ's native (non-Java) code used in reading and writing data.
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a> 5/4/12
 */
// XXX
public final class HornetQNativeIOError extends HornetQException
{
   private static final long serialVersionUID = 2355120980683293085L;

   public HornetQNativeIOError()
   {
      super(HornetQExceptionType.NATIVE_ERROR_CANT_INITIALIZE_AIO);
   }

   public HornetQNativeIOError(String msg)
   {
      super(HornetQExceptionType.NATIVE_ERROR_CANT_INITIALIZE_AIO, msg);
   }
}
