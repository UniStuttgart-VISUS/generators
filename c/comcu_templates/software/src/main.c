/* <<<DEVICE_NAME_DASH>>>
 * Copyright (C) <<<YEAR>>> <<<NAME>>> <<<<EMAIL>>>>
 *
 * main.c: Initialization for <<<DEVICE_NAME_READABLE>>>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

#include <stdio.h>
#include <stdbool.h>

#include "configs/config.h"

#include "bricklib2/bootloader/bootloader.h"
#include "bricklib2/hal/system_timer/system_timer.h"
#include "bricklib2/hal/uartbb/uartbb.h"
#include "communication.h"

#define SYSTEM_TIMER_FREQUENCY 1000 // Use 1 kHz system timer

int main(void) {
	system_timer_init(SystemCoreClock, SYSTEM_TIMER_FREQUENCY);
	uartbb_init();
	uartbb_puts("Start <<<DEVICE_NAME_READABLE>>>\n\r");

	while(true) {
		bootloader_tick();
	}

}
