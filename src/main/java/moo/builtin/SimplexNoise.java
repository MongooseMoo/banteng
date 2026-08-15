package moo.builtin;

/** Direct Java port of ToastStunt's public-domain SimplexNoise1234 value functions. */
final class SimplexNoise {
  private static final int[] BASE = {
    151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,
    37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
    57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,134,139,48,27,
    166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,
    102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,200,196,135,130,
    116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,250,124,123,5,202,38,147,
    118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,
    119,248,152,2,44,154,163,70,221,153,101,155,167,43,172,9,129,22,39,253,19,98,108,
    110,79,113,224,232,178,185,112,104,218,246,97,228,251,34,242,193,238,210,144,12,
    191,179,162,241,81,51,145,235,249,14,239,107,49,192,214,31,181,199,106,157,184,84,
    204,176,115,121,50,45,127,4,150,254,138,236,205,93,222,114,67,29,24,72,243,141,
    128,195,78,66,215,61,156,180
  };
  private static final int[] PERM = new int[512];
  private static final int[][] SIMPLEX = {
    {0,1,2,3},{0,1,3,2},{0,0,0,0},{0,2,3,1},{0,0,0,0},{0,0,0,0},{0,0,0,0},{1,2,3,0},
    {0,2,1,3},{0,0,0,0},{0,3,1,2},{0,3,2,1},{0,0,0,0},{0,0,0,0},{0,0,0,0},{1,3,2,0},
    {0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},
    {1,2,0,3},{0,0,0,0},{1,3,0,2},{0,0,0,0},{0,0,0,0},{0,0,0,0},{2,3,0,1},{2,3,1,0},
    {1,0,2,3},{1,0,3,2},{0,0,0,0},{0,0,0,0},{0,0,0,0},{2,0,3,1},{0,0,0,0},{2,1,3,0},
    {0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0},
    {2,0,1,3},{0,0,0,0},{0,0,0,0},{0,0,0,0},{3,0,1,2},{3,0,2,1},{0,0,0,0},{3,1,2,0},
    {2,1,0,3},{0,0,0,0},{0,0,0,0},{0,0,0,0},{3,1,0,2},{0,0,0,0},{3,2,0,1},{3,2,1,0}
  };

  static {
    for (int index = 0; index < PERM.length; index++) PERM[index] = BASE[index & 255];
  }

  private SimplexNoise() {}

  static double noise(double... coordinate) {
    return switch (coordinate.length) {
      case 1 -> noise1(coordinate[0]);
      case 2 -> noise2(coordinate[0], coordinate[1]);
      case 3 -> noise3(coordinate[0], coordinate[1], coordinate[2]);
      case 4 -> noise4(coordinate[0], coordinate[1], coordinate[2], coordinate[3]);
      default -> throw new IllegalArgumentException("simplex dimension must be 1 through 4");
    };
  }

  private static int floor(double value) {
    int truncated = (int) value;
    return truncated <= value ? truncated : truncated - 1;
  }

  private static double grad1(int hash, double x) {
    int h = hash & 15;
    double gradient = 1.0 + (h & 7);
    return ((h & 8) == 0 ? gradient : -gradient) * x;
  }

  private static double grad2(int hash, double x, double y) {
    int h = hash & 7;
    double u = h < 4 ? x : y;
    double v = h < 4 ? y : x;
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? 2.0 * v : -2.0 * v);
  }

  private static double grad3(int hash, double x, double y, double z) {
    int h = hash & 15;
    double u = h < 8 ? x : y;
    double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }

  private static double grad4(int hash, double x, double y, double z, double w) {
    int h = hash & 31;
    double u = h < 24 ? x : y;
    double v = h < 16 ? y : z;
    double q = h < 8 ? z : w;
    return ((h & 1) == 0 ? u : -u)
        + ((h & 2) == 0 ? v : -v)
        + ((h & 4) == 0 ? q : -q);
  }

  private static double noise1(double x) {
    int i0 = floor(x);
    double x0 = x - i0;
    double x1 = x0 - 1.0;
    double t0 = 1.0 - x0 * x0;
    t0 *= t0;
    double t1 = 1.0 - x1 * x1;
    t1 *= t1;
    return 0.25 * (t0 * t0 * grad1(PERM[i0 & 255], x0)
        + t1 * t1 * grad1(PERM[(i0 + 1) & 255], x1));
  }

  private static double noise2(double x, double y) {
    final double f = 0.366025403, g = 0.211324865;
    double s = (x + y) * f;
    int i = floor(x + s), j = floor(y + s);
    double t = (i + j) * g;
    double x0 = x - (i - t), y0 = y - (j - t);
    int i1 = x0 > y0 ? 1 : 0, j1 = x0 > y0 ? 0 : 1;
    double x1 = x0 - i1 + g, y1 = y0 - j1 + g;
    double x2 = x0 - 1.0 + 2.0 * g, y2 = y0 - 1.0 + 2.0 * g;
    int ii = i & 255, jj = j & 255;
    double n0 = contribution2(0.5 - x0*x0 - y0*y0, PERM[ii + PERM[jj]], x0, y0);
    double n1 = contribution2(0.5 - x1*x1 - y1*y1, PERM[ii+i1 + PERM[jj+j1]], x1, y1);
    double n2 = contribution2(0.5 - x2*x2 - y2*y2, PERM[ii+1 + PERM[jj+1]], x2, y2);
    return 40.0 * (n0 + n1 + n2);
  }

  private static double contribution2(double t, int hash, double x, double y) {
    if (t < 0.0) return 0.0;
    t *= t;
    return t * t * grad2(hash, x, y);
  }

  private static double noise3(double x, double y, double z) {
    final double f = 0.333333333, g = 0.166666667;
    double s = (x+y+z)*f;
    int i=floor(x+s), j=floor(y+s), k=floor(z+s);
    double t=(i+j+k)*g, x0=x-(i-t), y0=y-(j-t), z0=z-(k-t);
    int i1,j1,k1,i2,j2,k2;
    if (x0 >= y0) {
      if (y0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=1;k2=0; }
      else if (x0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=0;k2=1; }
      else { i1=0;j1=0;k1=1;i2=1;j2=0;k2=1; }
    } else {
      if (y0 < z0) { i1=0;j1=0;k1=1;i2=0;j2=1;k2=1; }
      else if (x0 < z0) { i1=0;j1=1;k1=0;i2=0;j2=1;k2=1; }
      else { i1=0;j1=1;k1=0;i2=1;j2=1;k2=0; }
    }
    double x1=x0-i1+g,y1=y0-j1+g,z1=z0-k1+g;
    double x2=x0-i2+2*g,y2=y0-j2+2*g,z2=z0-k2+2*g;
    double x3=x0-1+3*g,y3=y0-1+3*g,z3=z0-1+3*g;
    int ii=i&255,jj=j&255,kk=k&255;
    double n0=contribution3(0.5-x0*x0-y0*y0-z0*z0,PERM[ii+PERM[jj+PERM[kk]]],x0,y0,z0);
    double n1=contribution3(0.5-x1*x1-y1*y1-z1*z1,PERM[ii+i1+PERM[jj+j1+PERM[kk+k1]]],x1,y1,z1);
    double n2=contribution3(0.5-x2*x2-y2*y2-z2*z2,PERM[ii+i2+PERM[jj+j2+PERM[kk+k2]]],x2,y2,z2);
    double n3=contribution3(0.5-x3*x3-y3*y3-z3*z3,PERM[ii+1+PERM[jj+1+PERM[kk+1]]],x3,y3,z3);
    return 72.0*(n0+n1+n2+n3);
  }

  private static double contribution3(double t,int hash,double x,double y,double z) {
    if(t<0) return 0;
    t*=t;
    return t*t*grad3(hash,x,y,z);
  }

  private static double noise4(double x,double y,double z,double w) {
    final double f=0.309016994,g=0.138196601;
    double s=(x+y+z+w)*f;
    int i=floor(x+s),j=floor(y+s),k=floor(z+s),l=floor(w+s);
    double t=(i+j+k+l)*g,x0=x-(i-t),y0=y-(j-t),z0=z-(k-t),w0=w-(l-t);
    int c=(x0>y0?32:0)+(x0>z0?16:0)+(y0>z0?8:0)+(x0>w0?4:0)+(y0>w0?2:0)+(z0>w0?1:0);
    int i1=SIMPLEX[c][0]>=3?1:0,j1=SIMPLEX[c][1]>=3?1:0,k1=SIMPLEX[c][2]>=3?1:0,l1=SIMPLEX[c][3]>=3?1:0;
    int i2=SIMPLEX[c][0]>=2?1:0,j2=SIMPLEX[c][1]>=2?1:0,k2=SIMPLEX[c][2]>=2?1:0,l2=SIMPLEX[c][3]>=2?1:0;
    int i3=SIMPLEX[c][0]>=1?1:0,j3=SIMPLEX[c][1]>=1?1:0,k3=SIMPLEX[c][2]>=1?1:0,l3=SIMPLEX[c][3]>=1?1:0;
    double x1=x0-i1+g,y1=y0-j1+g,z1=z0-k1+g,w1=w0-l1+g;
    double x2=x0-i2+2*g,y2=y0-j2+2*g,z2=z0-k2+2*g,w2=w0-l2+2*g;
    double x3=x0-i3+3*g,y3=y0-j3+3*g,z3=z0-k3+3*g,w3=w0-l3+3*g;
    double x4=x0-1+4*g,y4=y0-1+4*g,z4=z0-1+4*g,w4=w0-1+4*g;
    int ii=i&255,jj=j&255,kk=k&255,ll=l&255;
    double n0=contribution4(0.5-x0*x0-y0*y0-z0*z0-w0*w0,PERM[ii+PERM[jj+PERM[kk+PERM[ll]]]],x0,y0,z0,w0);
    double n1=contribution4(0.5-x1*x1-y1*y1-z1*z1-w1*w1,PERM[ii+i1+PERM[jj+j1+PERM[kk+k1+PERM[ll+l1]]]],x1,y1,z1,w1);
    double n2=contribution4(0.5-x2*x2-y2*y2-z2*z2-w2*w2,PERM[ii+i2+PERM[jj+j2+PERM[kk+k2+PERM[ll+l2]]]],x2,y2,z2,w2);
    double n3=contribution4(0.5-x3*x3-y3*y3-z3*z3-w3*w3,PERM[ii+i3+PERM[jj+j3+PERM[kk+k3+PERM[ll+l3]]]],x3,y3,z3,w3);
    double n4=contribution4(0.5-x4*x4-y4*y4-z4*z4-w4*w4,PERM[ii+1+PERM[jj+1+PERM[kk+1+PERM[ll+1]]]],x4,y4,z4,w4);
    return 62.0*(n0+n1+n2+n3+n4);
  }

  private static double contribution4(double t,int hash,double x,double y,double z,double w) {
    if(t<0) return 0;
    t*=t;
    return t*t*grad4(hash,x,y,z,w);
  }
}
