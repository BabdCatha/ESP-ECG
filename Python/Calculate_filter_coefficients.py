#https://dsp.stackexchange.com/questions/59688/how-to-design-a-digital-filter-in-python-that-will-run-over-an-uc

from scipy.signal import butter, freqs
import matplotlib.pyplot as plt
from math import pi
import numpy as np

f_s = 500    # Sample frequency in Hz
f_c = 40     # Cut-off frequency in Hz
order = 4    # Order of the butterworth filter

omega_c = 2 * pi * f_c       # Cut-off angular frequency
omega_c_d = omega_c / f_s    # Normalized cut-off frequency (digital)

# Design the digital Butterworth filter
b, a = butter(order, omega_c_d / pi)
print('Coefficients')
print("b =", b)                           # Print the coefficients
print("a =", a)