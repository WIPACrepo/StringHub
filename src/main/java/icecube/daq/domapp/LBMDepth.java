package icecube.daq.domapp;

/**
 * These are the various memory lengths you can set inside the DOM should
 * you find the need to make the effective memory size shorter than the
 * physical maximum of 8 MB.  Note by default DOMApp sets it to 2 MB.
 * As a reminder the memory is divided into 2 kB slots. 
 * @author kael
 *
 */
public enum LBMDepth 
{
    LBM_256,
    LBM_512,
    LBM_1K,
    LBM_2K,
    LBM_4K,
    LBM_8K,
    LBM_16K,
    LBM_32K,
    LBM_64K,
    LBM_128K,
    LBM_256K,
    LBM_512K,
    LBM_1M,
    LBM_2M,
    LBM_4M,
    LBM_8M,
    LBM_16M;
}
