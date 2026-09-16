import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const syncProfileName = (currentUser) => {
    import('../services/api').then(({ profileService }) => {
      profileService.getProfile()
        .then(res => {
          if (res?.data) {
            const updated = { 
              ...currentUser, 
              name: res.data.name || currentUser.name,
              firstName: res.data.firstName || currentUser.firstName,
              lastName: res.data.lastName || currentUser.lastName,
              email: res.data.email || currentUser.email
            };
            localStorage.setItem('authUser', JSON.stringify(updated));
            setUser(updated);
          }
        })
        .catch(() => {});
    });
  };

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem('authToken');
      const storedUser = localStorage.getItem('authUser');
      if (storedToken) {
        try {
          const { authService } = await import('../services/api');
          const res = await authService.getCurrentUser();
          if (res?.data) {
            const userData = res.data;
            const roles = userData.roles || (userData.role ? [`ROLE_${userData.role.toUpperCase()}`] : []);
            const normalizedUser = { ...userData, roles };
            localStorage.setItem('authUser', JSON.stringify(normalizedUser));
            setUser(normalizedUser);
          } else {
            logout();
          }
        } catch (err) {
          console.error("Token verification failed:", err);
          if (err.response && err.response.status === 401) {
            logout();
          } else if (storedUser) {
            const parsedUser = JSON.parse(storedUser);
            const roles = parsedUser.roles || (parsedUser.role ? [`ROLE_${parsedUser.role.toUpperCase()}`] : []);
            setUser({ ...parsedUser, roles });
          } else {
            logout();
          }
        }
      } else {
        setUser(null);
      }
      setLoading(false);
    };

    initAuth();
  }, []);

  const login = (token, userData) => {
    const roles = userData.roles || (userData.role ? [`ROLE_${userData.role.toUpperCase()}`] : []);
    const normalizedUser = { ...userData, roles };
    
    localStorage.setItem('authToken', token);
    localStorage.setItem('authUser', JSON.stringify(normalizedUser));
    setUser(normalizedUser);
    syncProfileName(normalizedUser);
  };

  const logout = () => {
    localStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
    setUser(null);
  };

  const updateUser = (newData) => {
    setUser(prev => {
      if (!prev) return null;
      const updated = { ...prev, ...newData };
      localStorage.setItem('authUser', JSON.stringify(updated));
      return updated;
    });
  };

  const isAuthenticated = () => {
    const storedToken = localStorage.getItem('authToken');
    return !!user && !!storedToken;
  };

  const isAdmin = () => {
    return user?.role === 'ADMIN' || user?.roles?.includes('ROLE_ADMIN');
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser, isAuthenticated, isAdmin, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};

